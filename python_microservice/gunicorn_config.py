# Gunicorn configuration file
# This ensures JSON error responses even when gunicorn handles errors

import sys
import traceback
import json
from flask import jsonify

def on_starting(server):
    """Called just before the master process is initialized."""
    pass

def on_reload(server):
    """Called to recycle workers during a reload via SIGHUP."""
    pass

def when_ready(server):
    """Called just after the server is started."""
    server.log.info("Server is ready. Spawning workers")

def pre_fork(server, worker):
    """Called just before a worker is forked."""
    pass

def post_fork(server, worker):
    """Called just after a worker has been forked."""
    pass

def post_worker_init(worker):
    """Called just after a worker has initialized the application."""
    pass

def worker_int(worker):
    """Called when a worker receives the INT or QUIT signal."""
    pass

def pre_exec(server):
    """Called just before a new master process is forked."""
    pass

def worker_abort(worker):
    """Called when a worker receives the ABRT signal."""
    pass

def on_exit(server):
    """Called just before exiting Gunicorn."""
    pass

# Error handling hooks
def on_error(server, req):
    """Called when an error occurs."""
    server.log.error(f"Error processing request: {req}")
    # Return JSON error response
    error_response = {
        "error": "Internal server error",
        "message": "An error occurred processing the request"
    }
    return json.dumps(error_response), 500, {'Content-Type': 'application/json'}

# Worker timeout (5 minutes for large file processing)
timeout = 300
keepalive = 5
worker_class = 'sync'
workers = 1  # Single worker for now
bind = '127.0.0.1:5050'
accesslog = '-'
errorlog = '-'
loglevel = 'info'

