package com.example.commerce.ucp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Shopify UCP-over-JSON-RPC binding: narrowly scoped known tools, no arbitrary LLM tool execution. */
@Component
@ConditionalOnProperty(name = "commerce.mode", havingValue = "shopify")
public class ShopifyUcpGateway implements UcpGateway {
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String profile;
    private final String checkoutToken;

    public ShopifyUcpGateway(@Value("${commerce.agent-profile-url}") String profile,
                            @Value("${commerce.checkout-bearer-token:}") String checkoutToken) {
        this.profile = profile;
        this.checkoutToken = checkoutToken;
    }

    @Override public Discovery discover(String origin) {
        try {
            URI base = trustedHttps(origin);
            URI wellKnown = base.resolve("/.well-known/ucp");
            var request = HttpRequest.newBuilder(wellKnown)
                .timeout(Duration.ofSeconds(15)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("Business profile UCP não encontrado: HTTP " + response.statusCode());
            JsonNode root = json.readTree(response.body());
            JsonNode services = root.path("ucp").path("services").path("dev.ucp.shopping");
            if (!services.isArray()) throw new IllegalStateException("Serviço dev.ucp.shopping ausente no UCP profile");
            for (JsonNode service : services) {
                if ("mcp".equals(service.path("transport").asText())) {
                    String endpoint = service.path("endpoint").asText("");
                    URI target = trustedHttps(endpoint);
                    if (!Objects.equals(base.getHost(), target.getHost()) || base.getPort() != target.getPort())
                        throw new IllegalStateException("MCP endpoint fora da origem configurada");
                    return new Discovery(endpoint);
                }
            }
            throw new IllegalStateException("Loja não publicou transporte MCP no /.well-known/ucp");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discovery interrompido", e);
        } catch (Exception e) {
            throw new IllegalStateException("Discovery UCP falhou: " + e.getMessage(), e);
        }
    }

    @Override public List<String> listTools(String endpoint) {
        try {
            trustedHttps(endpoint);
            var requestJson = json.createObjectNode();
            requestJson.put("jsonrpc", "2.0");
            requestJson.put("id", UUID.randomUUID().toString());
            requestJson.put("method", "tools/list");
            var request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestJson)))
                .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("tools/list HTTP " + response.statusCode());
            var parsed = json.readTree(response.body());
            if (parsed.hasNonNull("error")) throw new IllegalStateException("tools/list retornou erro JSON-RPC");
            var tools = parsed.path("result").path("tools");
            if (!tools.isArray()) throw new IllegalStateException("tools/list não retornou uma lista");
            List<String> names = new ArrayList<>();
            for (var tool : tools) if (tool.hasNonNull("name")) names.add(tool.path("name").asText());
            return List.copyOf(names);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Discovery tools interrompido", e);
        } catch (Exception e) {
            throw new IllegalStateException("Discovery tools falhou: " + e.getMessage(), e);
        }
    }

    @Override public List<Product> search(String endpoint, String query) {
        JsonNode result = call(endpoint, "search_catalog", Map.of("catalog", Map.of("query", query)), false);
        JsonNode products = result.path("catalog").path("products");
        if (!products.isArray()) products = result.path("products");
        if (!products.isArray()) throw new IllegalStateException("Formato de catálogo diferente do esperado; confira o schema MCP da loja");
        List<Product> list = new ArrayList<>();
        for (JsonNode p : products) {
            String title = p.path("title").asText("Produto");
            String price = p.path("price_range").toString();
            JsonNode variants = p.path("variants");
            if (variants.isArray() && !variants.isEmpty()) {
                for (JsonNode v : variants) {
                    String variantId = v.path("id").asText("");
                    if (variantId.startsWith("gid://shopify/ProductVariant/"))
                        list.add(new Product(title, variantId, price));
                }
            } else {
                // Explicit variant selection is required: never mistake a product ID for a cart variant.
                list.add(new Product(title, "", price));
            }
        }
        return list;
    }

    @Override public Cart createCart(String endpoint, List<LineItem> items) {
        JsonNode result = call(endpoint, "create_cart", Map.of("cart", Map.of("line_items", lineItems(items))), false);
        return parseCart(result);
    }

    @Override public Cart getCart(String endpoint, String id) {
        return parseCart(call(endpoint, "get_cart", Map.of("id", id), false));
    }

    @Override public Cart replaceCart(String endpoint, String id, List<LineItem> items) {
        // Shopify update_cart has PUT semantics. Preserve buyer/context/attribution fields from fresh remote snapshot.
        JsonNode prior = call(endpoint, "get_cart", Map.of("id", id), false).path("cart");
        ObjectNode cart = json.createObjectNode();
        cart.set("line_items", json.valueToTree(lineItems(items)));
        for (String field : List.of("context", "attribution", "buyer", "signals")) {
            if (prior.has(field) && !prior.get(field).isNull()) cart.set(field, prior.get(field));
        }
        return parseCart(call(endpoint, "update_cart", Map.of("id", id, "cart", cart), false));
    }

    @Override public Checkout createCheckout(String endpoint, String cartId) {
        if (checkoutToken.isBlank())
            throw new IllegalStateException("Configure SHOPIFY_CHECKOUT_BEARER_TOKEN para criar checkout real");
        JsonNode content = call(endpoint, "create_checkout",
            Map.of("checkout", Map.of("cart_id", cartId, "line_items", List.of())), true);
        JsonNode node = content.has("checkout") ? content.path("checkout") : content;
        String id = node.path("id").asText("");
        if (id.isBlank()) throw new IllegalStateException("Checkout MCP não retornou ID");
        return new Checkout(id, node.path("continue_url").asText(""), node.path("status").asText("incomplete"));
    }

    private List<Map<String, Object>> lineItems(List<LineItem> items) {
        return items.stream().map(i -> Map.<String, Object>of(
            "quantity", i.quantity(), "item", Map.of("id", i.variantId()))).toList();
    }

    private Cart parseCart(JsonNode content) {
        JsonNode node = content.path("cart");
        String id = node.path("id").asText("");
        if (id.isBlank()) throw new IllegalStateException("Cart MCP não retornou cart.id");
        List<LineItem> items = new ArrayList<>();
        for (JsonNode line : node.path("line_items")) {
            items.add(new LineItem(line.path("item").path("id").asText(), line.path("quantity").asInt()));
        }
        return new Cart(id, List.copyOf(items), node.path("continue_url").asText(""));
    }

    private JsonNode call(String endpoint, String tool, Map<String, ?> input, boolean authenticated) {
        try {
            trustedHttps(endpoint);
            ObjectNode args = json.valueToTree(input);
            ObjectNode meta = args.putObject("meta");
            meta.putObject("ucp-agent").put("profile", profile);
            var rpc = json.createObjectNode();
            rpc.put("jsonrpc", "2.0");
            rpc.put("method", "tools/call");
            rpc.put("id", UUID.randomUUID().toString());
            rpc.putObject("params").put("name", tool).set("arguments", args);
            var builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(rpc)));
            if (authenticated) builder.header("Authorization", "Bearer " + checkoutToken);
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("MCP " + tool + " retornou HTTP " + response.statusCode());
            JsonNode envelope = json.readTree(response.body());
            if (envelope.hasNonNull("error")) throw new IllegalStateException("Erro JSON-RPC na operação " + tool);
            JsonNode result = envelope.path("result");
            if (result.path("isError").asBoolean(false)) throw new IllegalStateException("Tool MCP falhou: " + tool);
            JsonNode structured = result.path("structuredContent");
            if (structured.isMissingNode() || structured.isNull())
                throw new IllegalStateException("Resposta sem structuredContent: " + tool);
            return structured;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada MCP interrompida", e);
        } catch (Exception e) {
            throw new IllegalStateException("Falha na tool " + tool + ": " + e.getMessage(), e);
        }
    }

    private static URI trustedHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("UCP requer URL HTTPS confiável sem credenciais embutidas");
        return uri;
    }
}
