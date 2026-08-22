import os
import sys
from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler
from pyftpdlib.servers import FTPServer

sys.stderr.write(f"DEBUG1: Before authorizer setup, FTPHandler.authorizer users = {list(FTPHandler.authorizer.user_table.keys())}\n")
sys.stderr.flush()

authorizer = DummyAuthorizer()
authorizer.add_user("anonymous", "", "/srv/ftp", perm="elradfmwMT")

sys.stderr.write(f"DEBUG2: After add_user, authorizer users = {list(authorizer.user_table.keys())}\n")
sys.stderr.write(f"DEBUG2: FTPHandler.authorizer before set = {id(FTPHandler.authorizer)}\n")

FTPHandler.authorizer = authorizer

sys.stderr.write(f"DEBUG3: After set, FTPHandler.authorizer = {id(FTPHandler.authorizer)}\n")
sys.stderr.write(f"DEBUG3: FTPHandler.authorizer users = {list(FTPHandler.authorizer.user_table.keys())}\n")
sys.stderr.flush()

class DockerFTPHandler(FTPHandler):
    masquerade_address = '127.0.0.1'
    passive_ports = range(30000, 30010)

sys.stderr.write(f"DEBUG4: DockerFTPHandler.authorizer users = {list(DockerFTPHandler.authorizer.user_table.keys())}\n")
sys.stderr.write(f"DEBUG4: Same as FTPHandler? {DockerFTPHandler.authorizer is FTPHandler.authorizer}\n")
sys.stderr.flush()

server = FTPServer(("0.0.0.0", 21), DockerFTPHandler)
server.max_cons = 50
server.max_cons_per_ip = 10
server.serve_forever()
