#!/usr/bin/python3

from flask import Flask
from flask import request
from flask import make_response

app = Flask(__name__)

@app.route('/<arg1>/<op>/<arg2>', methods=['GET'])
def calculate_get(arg1, op, arg2):
    resp = 0
    if op == '+':
        result = int(arg1) + int(arg2)
        resp = {
            "status": "200 OK",
            "result": result
        }
    elif op == '-':
        result = int(arg1) - int(arg2)
        resp = {
            "status": "200 OK",
            "result": result
        }
    elif op == '*':
        result = int(arg1) * int(arg2)
        resp = {
            "status": "200 OK",
            "result": result
        }
    else:
        resp = make_response("비정상적인 입력", 400)
    
    return resp

@app.route('/', methods=['POST'])
def calculate_post():
    resp = 0
    arg1 = request.get_json().get('arg1')
    arg2 = request.get_json().get('arg2')
    op = request.get_json().get('op')
    if op == '+':
        result = int(arg1) + int(arg2)
        resp = {
            "status": "200 OK",
            "result": result
        }
    elif op == '-':
        result = int(arg1) - int(arg2)
        resp = {
            "status": "200 OK",
            "result": result
        }
    elif op == '*':
        result = int(arg1) * int(arg2)
        resp = {
            "status": "200 OK",
            "result": result
        }
    else:
        resp = make_response("비정상적인 입력", 400)
    
    return resp


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=19115)
