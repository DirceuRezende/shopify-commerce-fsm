"""Small single-server MCP Streamable HTTP mock for development (NOT a full UCP implementation).
Supports MCP initialize, tools/list and tools/call using actual remote tool definitions.
"""
import json
import os
import uuid
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

PORT = int(os.getenv('MOCK_UCP_PORT', '8090'))
PUBLIC_ORIGIN = os.getenv('MOCK_UCP_ORIGIN', f'http://localhost:{PORT}')
CARTS = {}
LOCK = threading.RLock()
CATALOG = [
    {'title': 'Tênis Running Azul', 'variantId': 'gid://shopify/ProductVariant/101', 'price': '299.90'},
    {'title': 'Tênis Running Preto', 'variantId': 'gid://shopify/ProductVariant/102', 'price': '349.90'},
    {'title': 'Camiseta Dry Fit', 'variantId': 'gid://shopify/ProductVariant/103', 'price': '79.90'},
]

def obj(props, required=None):
    return {'type': 'object', 'properties': props, 'required': required or []}

LINE = obj({'item':obj({'id':{'type':'string'}}, ['id']), 'quantity':{'type':'integer','minimum':1}}, ['item','quantity'])
CART = obj({'id':{'type':'string'},'line_items':{'type':'array','items':LINE},'context':{'type':'object'},'attribution':{'type':'object'}}, ['line_items'])
TOOLS = [
    {'name': 'search_catalog', 'description':'Search product catalog for given query', 'inputSchema':obj({'catalog':obj({'query':{'type':'string'}}, ['query']), 'meta':{'type':'object'}}, ['catalog'])},
    {'name': 'lookup_catalog', 'description':'Look up products by identifier', 'inputSchema':obj({'catalog':obj({'ids':{'type':'array','items':{'type':'string'}}}, ['ids']), 'meta':{'type':'object'}}, ['catalog'])},
    {'name': 'get_product', 'description':'Details of a given variant or product', 'inputSchema':obj({'catalog':obj({'id':{'type':'string'}}, ['id']), 'meta':{'type':'object'}}, ['catalog'])},
    {'name': 'create_cart', 'description':'Creates cart with requested line items', 'inputSchema':obj({'cart':CART, 'meta':{'type':'object'}}, ['cart'])},
    {'name': 'get_cart', 'description':'Returns cart by id', 'inputSchema':obj({'id':{'type':'string'}, 'meta':{'type':'object'}}, ['id'])},
    {'name': 'update_cart', 'description':'Replaces complete cart document', 'inputSchema':obj({'id':{'type':'string'}, 'cart':CART, 'meta':{'type':'object'}}, ['id','cart'])},
    {'name': 'create_checkout', 'description':'Creates checkout session from cart; no payment', 'inputSchema':obj({'checkout':obj({'cart_id':{'type':'string'},'line_items':{'type':'array','items':LINE}}, ['cart_id']), 'meta':{'type':'object'}}, ['checkout'])},
    {'name': 'complete_checkout', 'description':'Not allowed by Java agents; mock advertises it to test filtering', 'inputSchema':obj({'id':{'type':'string'}}, ['id'])},
]

def tool(name, args):
    if 'profile' not in args.get('meta',{}).get('ucp-agent',{}):
        raise ValueError('meta.ucp-agent.profile missing')
    if name == 'search_catalog':
        query = args.get('catalog', {}).get('query','').lower()
        return {'products':[p for p in CATALOG if not query or query in p['title'].lower() or ('tenis' in query and 'tênis' in p['title'].lower())]}
    if name in ('get_product','lookup_catalog'):
        needle = args.get('catalog',{})
        ids = needle.get('ids',[needle.get('id','')])
        return {'products':[p for p in CATALOG if p['variantId'] in ids]}
    if name == 'create_cart':
        cart = args.get('cart') or {}
        if not cart.get('line_items'): raise ValueError('line_items required')
        ident = 'gid://shopify/Cart/mock_' + str(uuid.uuid4())
        value = {'id':ident, 'line_items':cart['line_items'], 'context':cart.get('context',{}), 'continue_url':PUBLIC_ORIGIN+'/cart'}
        with LOCK: CARTS[ident] = value
        return {'cart':value}
    if name in ('get_cart','update_cart'):
        ident = args.get('id','')
        with LOCK:
            if ident not in CARTS: raise ValueError('cart not found')
            if name == 'update_cart':
                incoming = args.get('cart') or {}
                if 'line_items' not in incoming: raise ValueError('PUT cart.line_items required')
                CARTS[ident] = {'id':ident, 'line_items':incoming['line_items'], 'context':incoming.get('context',{}), 'continue_url':PUBLIC_ORIGIN+'/cart'}
            return {'cart':CARTS[ident]}
    if name == 'create_checkout':
        ident = args.get('checkout',{}).get('cart_id','')
        with LOCK:
            if ident not in CARTS or not CARTS[ident]['line_items']: raise ValueError('nonempty cart required')
        cid = 'gid://shopify/Checkout/mock_' + str(uuid.uuid4())
        return {'checkout':{'id':cid,'continue_url':PUBLIC_ORIGIN+'/checkout/'+cid.split('/')[-1],'status':'requires_escalation'}}
    if name == 'complete_checkout':
        raise ValueError('Never implemented in mock')
    raise ValueError('Tool not found: '+name)

class Handler(BaseHTTPRequestHandler):
    def send_json(self, status, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Content-Length',str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == '/.well-known/ucp':
            return self.send_json(200,{'ucp': {'version':'2026-08-25','services':{'dev.ucp.shopping':[{'transport':'mcp','endpoint':PUBLIC_ORIGIN+'/api/ucp/mcp'}]}}})
        if self.path == '/health': return self.send_json(200,{'status':'up'})
        self.send_error(405)

    def do_POST(self):
        if urlparse(self.path).path != '/api/ucp/mcp': return self.send_json(404, {'error':'not found'})
        try:
            size = int(self.headers.get('Content-Length','0'))
            if size > 100000: return self.send_json(413, {'error':'too large'})
            request = json.loads(self.rfile.read(size))
            method = request.get('method','')
            identifier = request.get('id')
            if method == 'notifications/initialized':
                self.send_response(202); self.send_header('Content-Length','0'); self.end_headers(); return
            if method == 'initialize':
                result = {'protocolVersion':'2025-06-18','capabilities':{'tools':{'listChanged':False}},'serverInfo':{'name':'ucp-mock','version':'0.1.0'}}
            elif method == 'tools/list':
                result = {'tools': TOOLS}
            elif method == 'tools/call':
                params = request.get('params',{})
                data = tool(params.get('name'), params.get('arguments',{}))
                result = {'structuredContent': data, 'content':[{'type':'text','text':json.dumps(data,ensure_ascii=False)}], 'isError': False}
            else:
                raise ValueError('Unknown MCP method: '+method)
            return self.send_json(200,{'jsonrpc':'2.0','id':identifier,'result':result})
        except Exception as e:
            return self.send_json(200,{'jsonrpc':'2.0','id':identifier if 'identifier' in locals() else None,
                                       'result':{'isError':True,'content':[{'type':'text','text':json.dumps({'error':str(e)})}]}})

if __name__ == '__main__':
    print(f'Mock UCP MCP listening on :{PORT}', flush=True)
    ThreadingHTTPServer(('0.0.0.0',PORT),Handler).serve_forever()
