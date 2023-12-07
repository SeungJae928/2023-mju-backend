from http import HTTPStatus
import random
import requests
import json
import urllib
import urllib.request
import mysql.connector

from flask import abort, Flask, make_response, render_template, Response, redirect, request

app = Flask(__name__)



naver_client_id = 'C4Ib26ua2LaF1lbEmhw9'
naver_client_secret = 'TqYRW_onZn'
naver_redirect_uri = 'http://60212192-lb-1288466503.ap-northeast-2.elb.amazonaws.com/memo/auth'
'''
    본인 app 의 것으로 교체할 것.
    여기 지정된 url 이 http://localhost:8000/auth 처럼 /auth 인 경우
    아래 onOAuthAuthorizationCodeRedirected() 에 @app.route('/auth') 태깅한 것처럼 해야 함
'''

# DB 설정
connection = mysql.connector.connect(
        host = '43.202.52.17',         # db 인스턴스 public ip
        port = '3306',                  # db 사용 포트
        database = 'mjubackend',        # db 이름
        user = 'root',                  # mysql user 이름
        password = 'tmdwo928@@'         # user의 password
)
cursor = connection.cursor()            # db 처리를 위한 cursor

@app.route('/')
def home():
    userId = request.cookies.get('userId', default=None)
    name = None

    if userId is not None:
        findNameById(cursor, userId)        # 로그인 쿠키가 존재하면 해당 유저의 이름 검색
        result = cursor.fetchone()[0]
        name = result if result else None

    return render_template('index.html', name=name)


@app.route('/login')
def onLogin():
    params={
            'response_type': 'code',
            'client_id': naver_client_id,
            'redirect_uri': naver_redirect_uri,
            'state': random.randint(0, 10000)
        }
    urlencoded = urllib.parse.urlencode(params)
    url = f'https://nid.naver.com/oauth2.0/authorize?{urlencoded}'
    return redirect(url)


@app.route('/auth')
def onOAuthAuthorizationCodeRedirected():
    authorization_code = request.args['code']   # /login 요청을 통해 받은 code를 가져옴
    state = request.args['state']               # /login 요청을 통해 받은 state를 가져옴
    params = {                                  # Access Token 발급 요청을 위한 파라미터
        'grant_type': 'authorization_code',
        'client_id': naver_client_id,
        'client_secret': naver_client_secret,
        'code': authorization_code,
        'state': state
    }
    urlencoded = urllib.parse.urlencode(params) # param을 url에 넣기위해 encode

    url = f'https://nid.naver.com/oauth2.0/token?{urlencoded}'  # Access Token 발급 요청 url
    token_request = requests.get(url)           # 요청
    token_json = token_request.json()           # 요청 결과를 json(dict type)으로 변환

    access_token = token_json.get('access_token')       # 결과에서 access_token을 가져옴
    url = 'https://openapi.naver.com/v1/nid/me'         # 프로필 조회 요청 url
    header = {'Authorization':f'Bearer {access_token}'} # 요청에 같이 보낼 Header
    profile_request = requests.get(url, headers=header) # 요청 
    profile_json = profile_request.json()               # 요청 결과를 json(dict type)으로 변환

    user_id = profile_json.get('response').get('id')        # 결과에서 id를 가져옴
    user_name = profile_json.get('response').get('name')    # 결과에서 name을 가져옴
    findUserById(cursor, user_id)               # db에 해당 유저가 존재하는지 검색
    if cursor.fetchone() is None:               
        addUser(cursor, user_id, user_name)     # 존재하지 않으면 해당 유저 데이터 입력

    response = redirect('/memo/')               # 홈 화면으로 redirect
    response.set_cookie('userId', user_id)      # 로그인 쿠키 설정
    return response


@app.route('/memo', methods=['GET'])
def get_memos():
    userId = request.cookies.get('userId', default=None)
    if not userId:
        return redirect('/memo/')

    result = []
    findMemosById(cursor, userId)       # 유저의 id로 등록된 메모들을 cursor에 올림
    data = cursor.fetchall()            # cursor의 데이터를 가져옴

    for row in data:
        result.append({
            'text': row[0]              # 각 메모를 {'text':내용} 형식으로 List에 추가
        })

    return {'memos': result}

@app.route('/memo', methods=['POST'])
def post_new_memo():
    userId = request.cookies.get('userId', default=None)
    if not userId:
        return redirect('/memo/')

    if not request.is_json:
        abort(HTTPStatus.BAD_REQUEST)

    response = request.json
    text = response['text']             # 클라이언트에서 받은 text를 가져옴
    addMemo(cursor, userId, text)       # 받은 text와 로그인 쿠키의 유저 id를 데이터베이스에 입력

    return '', HTTPStatus.OK


@app.route('/health', methods=['GET'])
def health_check():
    return '', HTTPStatus.OK


def findMemosById(cursor, id):
    connection.reconnect()
    query = 'SELECT text FROM memo WHERE user_id=%s'
    cursor.execute(query, (id,))

def findNameById(cursor, id):
    connection.reconnect()
    query = 'SELECT name FROM user WHERE id=%s'
    cursor.execute(query, (id,))

def findUserById(cursor, id):
    connection.reconnect()
    query = 'SELECT * FROM user WHERE id=%s'
    cursor.execute(query, (id,))

def addUser(cursor, id, name):
    connection.reconnect()
    query = 'INSERT INTO user (id, name) VALUES (%s, %s);'
    cursor.execute(query, (id, name))
    connection.commit()

def addMemo(cursor, id, text):
    connection.reconnect()
    query = 'INSERT INTO memo (user_id, text) VALUES (%s, %s)'
    cursor.execute(query, (id, text))
    connection.commit()

if __name__ == '__main__':
    app.run('0.0.0.0', port=8000, debug=True)