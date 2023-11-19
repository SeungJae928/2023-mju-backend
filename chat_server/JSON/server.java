package JSON;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;

public class server {

    public static int roomSeq = 1;
    public static List<Room> roomList = new ArrayList<>();
    static ServerSocket serverSocket;
    private static Socket socket;
    static List<ChatThread> threadList = new ArrayList<>();
    static boolean state = true;
    static int numOfThread = 10;
    static BlockingQueue<ChatThread> taskQueue = new ArrayBlockingQueue<>(numOfThread);
    private static List<WorkerThread> workerList = new ArrayList<>();
    private static final Lock lock = new ReentrantLock();
    private static final Condition messageCondition = lock.newCondition();

    public static void main(String[] args) throws IOException{
        int port = 19115;
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("localhost", port));
        
        for(int i = 0; i < numOfThread; i++){
            WorkerThread t = new WorkerThread();
            workerList.add(t);
            t.start();
        }
      
        try {
            while(!serverSocket.isClosed()) {
                socket = serverSocket.accept();
                ChatThread chatThread = new ChatThread(socket);
                threadList.add(chatThread);
                chatThread.start();
            }
        } catch (SocketException e) {
            System.out.println("Server closed");
        }
    }

    public static void shutdownServer() throws IOException{
        for(ChatThread thread : threadList){
            thread.interrupt();
            thread.getMember().getSock().close();
        }
        for(WorkerThread thread : workerList){
            thread.interrupt();
        }
        serverSocket.close();
    }

    public static void addTask(ChatThread client){
        try{
            lock.lock();
            server.taskQueue.add(client);
            messageCondition.signal();
        } finally {
            lock.unlock();
        }
    }

    static class WorkerThread extends Thread {

        @Override
        public void run() {
            try {
                while (true) {
                    try{
                        lock.lock();
                        while(taskQueue.isEmpty()){
                            messageCondition.await();
                        }
                        ChatThread task = taskQueue.take(); // 작업이 있을 때까지 블록
                        proceedTask(task);
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("WorkerThread:" + this.getId() + " Interrupted by /shutdown");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static void handleMessage(String type, ChatThread chatThread, JSONObject receiveObject) throws IOException{
            Member member = chatThread.getMember();
            DataOutputStream out = new DataOutputStream(member.getSock().getOutputStream());
            JSONObject sendObject;
            switch (type) {
                case "CSChat":
                    if(member.isJoin()){                                            // 방에 있는 자신을 제외한 다른 유저에게 보내기
                        String text = receiveObject.getString("text");
                        sendObject = new JSONObject();
                        sendObject.put("type", "SCChat");
                        sendObject.put("member", member.getName());
                        sendObject.put("text", text);
                        for(Member item: member.getRoom().getMembers()){
                            if(item.equals(member)){
                                continue;
                            }
                            out = new DataOutputStream(item.getSock().getOutputStream());
                            out.writeUTF(sendObject.toString());
                            out.flush();
                        }
                } else {
                    sendObject = new JSONObject();
                    sendObject.put("type", "SCSystemMessage");
                    sendObject.put("text", "현재 대화방에 들어가 있지 않습니다.");
                    out.writeUTF(sendObject.toString());
                    out.flush();
                }
                    break;
                case "CSName":
                    String name = receiveObject.getString("name");
                    if(member.isJoin()){
                        String prevName = member.getName();
                        member.setName(name);
                        sendObject = new JSONObject();
                        sendObject.put("type", "SCSystemMessage");
                        sendObject.put("text", prevName + " 의 이름이 " + name + " 으로 변경되었습니다.");
                        for(Member item: member.getRoom().getMembers()){
                            out = new DataOutputStream(item.getSock().getOutputStream());
                            out.writeUTF(sendObject.toString());
                            out.flush();
                        }
                    } else {
                        member.setName(name);
                        sendObject = new JSONObject();  
                        sendObject.put("type", "SCSystemMessage");
                        sendObject.put("text", "이름이 " + name + " 으로 변경되었습니다.");
                        out.writeUTF(sendObject.toString());
                        out.flush();
                    }
                    break;
                case "CSJoinRoom":
                    if(!member.isJoin()){                                           // 이미 채팅방에 소속된 유저 사용제한
                        int roomId = receiveObject.getInt("roomId");
                        boolean isJoined = false;
                        for(Room room : server.roomList){                           
                            if(room.getRoomId() == roomId){
                                room.joinRoom(member);
                                sendObject = new JSONObject();
                                sendObject.put("type", "SCSystemMessage");
                                sendObject.put("text", "방제 [" + member.getRoom().getTitle() + "] 방에 입장했습니다.");
                                out.writeUTF(sendObject.toString());
                                out.flush();    
                                isJoined = true;
                            }
                        }
                        if(!isJoined){
                            sendObject = new JSONObject();
                            sendObject.put("type", "SCSystemMessage");
                            sendObject.put("text", "대화방이 존재하지 않습니다.");
                            out.writeUTF(sendObject.toString());
                            out.flush();
                        } else {
                            sendObject = new JSONObject();
                            sendObject.put("type", "SCSystemMessage");
                            sendObject.put("text", "[" + member.getName() + "] 님이 입장했습니다.");
                            for(Member item: member.getRoom().getMembers()){
                                if(item.equals(member)){
                                    continue;
                                }
                                out = new DataOutputStream(item.getSock().getOutputStream());
                                out.writeUTF(sendObject.toString());
                                out.flush();
                            }
                        }
                    } else {
                        sendObject = new JSONObject();
                        sendObject.put("type", "SCSystemMessage");
                        sendObject.put("text", "대화 방에 있을 때는 다른 방에 들어갈 수 없습니다.");
                        out.writeUTF(sendObject.toString());
                        out.flush();
                    }
                    break;
                case "CSCreateRoom":
                    if(!member.isJoin()){                                           // 이미 채팅방에 소속된 유저 사용제한
                        String title = receiveObject.getString("title");           
                        Room room = new Room(title);
                        server.roomList.add(room);
                        room.joinRoom(member);
                        sendObject = new JSONObject();
                        sendObject.put("type", "SCSystemMessage");
                        sendObject.put("text", "방제 [" + member.getRoom().getTitle() + "] 방에 입장했습니다.");
                        out.writeUTF(sendObject.toString());
                        out.flush();    
                    } else {
                        sendObject = new JSONObject();
                        sendObject.put("type", "SCSystemMessage");
                        sendObject.put("text", "대화 방에 있을 때는 방을 개설 할 수 없습니다.");
                        out.writeUTF(sendObject.toString());
                        out.flush();
                    }
                    break;
                case "CSRooms":
                    JSONObject rooms = new JSONObject();
                    rooms.put("type", "SCRoomsResult");
                    List<JSONObject> roomList = new ArrayList<>();
                    for (Room room : server.roomList) {
                        sendObject = new JSONObject();
                        sendObject.put("roomId", room.getRoomId());
                        sendObject.put("title", room.getTitle());
                        sendObject.put("members", room.getMembersName());
                        roomList.add(sendObject);
                    }
                    rooms.put("rooms", roomList);
                    out.writeUTF(rooms.toString());
                    out.flush();
                    break;
                case "CSLeaveRoom":
                    if(member.isJoin()){        
                        Room currentRoom = member.getRoom();                        // 소속된 방이 있는 경우에만 작동
                        if(currentRoom.getMembersNum() == 1){                       // 유저가 한 명 밖에 없을 경우 채팅방 삭제
                            sendObject = new JSONObject();
                            sendObject.put("type", "SCSystemMessage");
                            sendObject.put("text", "[" + member.getName() + "] 님이 퇴장하셨습니다.");
                            out.writeUTF(sendObject.toString());
                            out.flush();
                            currentRoom.leaveRoom(member);
                            server.roomList.remove(currentRoom);
                        } else {
                            sendObject = new JSONObject();
                            sendObject.put("type", "SCSystemMessage");
                            sendObject.put("text", "[" + member.getName() + "] 님이 퇴장하셨습니다.");
                            for(Member item: member.getRoom().getMembers()){
                                out = new DataOutputStream(item.getSock().getOutputStream());
                                out.writeUTF(sendObject.toString());
                                out.flush();
                            }
                            currentRoom.leaveRoom(member);
                        }
                    } else {
                        sendObject = new JSONObject();
                        sendObject.put("type", "SCSystemMessage");
                        sendObject.put("text", "현재 대화방에 들어가 있지 않습니다.");
                        out.writeUTF(sendObject.toString());
                        out.flush();
                    }
                    break;
                case "CSShutdown":
                    server.shutdownServer();
                    break;
                default:
                    break;
            }
        }

        private static void proceedTask(ChatThread chatThread) throws IOException{
            JSONObject jsonObject = chatThread.getJsonObject();
            String type = jsonObject.getString("type");
            handleMessage(type, chatThread, jsonObject);
        }
    }
}


class Member {
    private final Socket sock;  
    private String name;
    private Room joinedRoom;

    public Member(Socket socket){
        this.sock = socket;
        this.name = "[" + sock.getInetAddress() + ", " + sock.getPort() + "]";
        this.joinedRoom = null;
    }

    public Socket getSock(){
        return this.sock;
    }

    public String getName(){
        return this.name;
    }

    public Room getRoom(){
        return this.joinedRoom;
    }

    public void setName(String name){
        this.name = name;
    }

    public boolean isJoin(){
        if(this.joinedRoom != null){
            return true;
        } else {
            return false;
        }
    }

    public void setJoinedRoom(Room room){
        this.joinedRoom = room;
    }
}


class Room {

    private int roomId;
    private String title;
    private final List<Member> memList = new ArrayList<>();

    public Room(String title){
        this.roomId = server.roomSeq++;
        this.title = title;
    }

    public void joinRoom(Member member){
        memList.add(member);
        member.setJoinedRoom(this);
    }

    public void leaveRoom(Member member){
        memList.remove(member);
        member.setJoinedRoom(null);
    }

    public int getRoomId(){
        return this.roomId;
    }

    public String getTitle(){
        return this.title;
    }

    public List<Member> getMembers(){
        return this.memList;
    }

    public List<String> getMembersName(){
        List<String> nameList = new ArrayList<>();
        for(Member member : memList){
            nameList.add(member.getName());
        }
        return nameList;
    }

    public int getMembersNum(){
        return memList.size();
    }
}



class ChatThread extends Thread {

    private final Member member;

    private JSONObject receivedJsonObject;
    
    public ChatThread(Socket socket) {
        this.member = new Member(socket);
    }

    public Member getMember(){
        return this.member;
    }

    public JSONObject getJsonObject(){
        return this.receivedJsonObject;
    }

    @Override
    public void run() {                                                             // TODO: 표준 출력을 각 클라이언트에 전송하기
        try {
            DataInputStream tmpbuf = new DataInputStream(member.getSock().getInputStream());
            String receiveString;
            while (true) {
                receiveString = tmpbuf.readUTF();
                this.receivedJsonObject = new JSONObject(receiveString);
                server.addTask(this);
            }
        } catch (SocketException e1) {
            System.out.println("상대방 연결이 종료되었습니다.");
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }
}
