#!/usr/bin/env python3
import tornado
import tornado.web
import tornado.httpclient
import tornado.ioloop
import sys
import time
import os
import socket
import hashlib
from urllib.parse import urlsplit 

db = {}

class ProxyHandler(tornado.web.RequestHandler):

    @tornado.gen.coroutine
    def get(self):
        print('Handle %s request to %s'%(self.request.method,self.request.uri))
        self.url_splited = urlsplit(self.request.uri)
        splited_path = self.url_splited.path if not self.url_splited.path.endswith("/") else "%s/index.html" % self.url_splited.path
        self.file_path = 'cache%s%s'%(self.url_splited.netloc,splited_path)

        def handle_response(response):
            print('start handling response')

            if response.error:
                print('responser error: %s'%(response.error))

            if response.body:
                self.set_status(response.code)
                # print(response.code)
                for header in ('Data','Server','Content-Type'):
                    val = response.headers.get(header)
                    if val:
                        self.set_header(header,val)
                val = response.headers.get_list('Set-Cookie')
                if val:
                    for item in val:
                        self.add_header('Set-Cookie',item)
                self.set_header('Cache-Control','no-store')

                dirname = os.path.dirname(self.file_path)
                if not os.path.exists(dirname):
                    os.makedirs(dirname)
                    
                with open(self.file_path,'wb') as f:
                    f.write(response.body)
                content_type = response.headers.get('Content-Type')
                content_len = os.path.getsize(self.file_path)
                etag = response.headers.get('ETag')
                if not etag:
                    md5 = hashlib.md5()
                    md5.update(response.body)
                    etag = md5.hexdigest()
                self.set_header('Content-Length',content_len)
                self.write(response.body)
                db[self.file_path] = {'cache_file_path':self.file_path,'content_type':content_type,'content_len':content_len,'etag':etag}
            print('finish response')

        item = db.get(self.file_path)
        # print(self.request.method)
        if not item or self.request.method != 'GET':
            print('cannot find %s'%(self.file_path))
            self.request.body = self.request.body or None
            try:
                response = yield fetch_url(self.request.uri,method = self.request.method,
                                           body = self.request.body,headers = self.request.headers,
                                           allow_nonstandard_methods = True)
                handle_response(response)
            except Exception as e:
                if hasattr(e,'response') and e.response:
                    handle_response(e.response)
                    self.finish()
                else:
                    self.set_status(500)
                    self.write('Internal server error:\n%s'%(str(e)))
                    self.finish()
                
        else:
            print('read from cache')
            self.set_header("Content-Type",item['content_type'])
            self.set_header("Content-Length",item['content_len'])
            self.set_header("Cache-Control","no-store")
            with open(item['cache_file_path'],"rb") as f:
                self.write(f.read())
            print('write finished')
            self.finish()
            
    @tornado.web.asynchronous
    def post(self):
        return self.get()            

@tornado.gen.coroutine        
def fetch_url(url,**kwargs):
    client = tornado.httpclient.AsyncHTTPClient()

    kwargs['validate_cert'] = False
    kwargs['connect_timeout'] = 1
    kwargs['request_timeout'] = 5
    request = tornado.httpclient.HTTPRequest('http:/'+url,**kwargs)
    # print(request)
    # print(kwargs)
    try:
        response = yield tornado.gen.with_timeout(tornado.ioloop.IOLoop.current().time()+5,client.fetch(request))
    except Exception as e:
        print(e)
        response = tornado.httpclient.HTTPResponse(request,504)
    raise tornado.gen.Return(response)
    
if __name__ == '__main__':
    if len(sys.argv) <= 1:
        print('Usage: "ProxyServer.py server_ip"\n[server_ip: It is the IP address of proxy server.]')
        sys.exit(2)
    try:
        application = tornado.web.Application([(r'.*',ProxyHandler)])
        application.listen(1999,sys.argv[1])
        print('ready to serve...')
        tornado.ioloop.IOLoop.current().start()
    except KeyboardInterrupt:
        sys.exit(1)
