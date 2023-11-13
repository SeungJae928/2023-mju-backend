import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class server {

    public static int roomSeq = 1;
    public static List<Room> roomList = new ArrayList<>();
    private static ServerSocket serverSocket;
    
    public static void main(String[] args) throws IOException{
        List<Thread> threadList = new ArrayList<>();
        int port = 9115;
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("localhost", port));
      
        while(true) {
            Socket socket = serverSocket.accept();
            ReceiveThread receiveThread = new ReceiveThread(socket);
            threadList.add(receiveThread);
            receiveThread.start();
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

// Room class roomId, title, 접속중인 멤버를 담는다.
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

class ReceiveThread extends Thread {

    private final Member member;
    
    public ReceiveThread(Socket socket) {
        this.member = new Member(socket);
    }

    @Override
    public void run() {                                                             // TODO: 표준 출력을 각 클라이언트에 전송하기
        try {
            DataInputStream tmpbuf = new DataInputStream(member.getSock().getInputStream());
            DataOutputStream out = new DataOutputStream(member.getSock().getOutputStream());
            String receiveString;
            while (true) {
                receiveString = tmpbuf.readUTF();
                JSONObject jsonObject = new JSONObject(receiveString);
                JSONObject sendObject;
                String type = jsonObject.getString("type");
                if(type.equals("CSChat")){                                          // 일반 채팅
                    if(member.isJoin()){                                            // 방에 있는 자신을 제외한 다른 유저에게 보내기
                        String text = jsonObject.getString("text");
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
                }
                if(type.equals("CSName")){                                          // 이름 바꾸기 완
                    String name = jsonObject.getString("name");
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
                }
                if(type.equals("CSRooms")){                                         // 방 목록 출력하기 완
                    JSONObject rooms = new JSONObject();
                    rooms.put("type", "SCRoomsResult");
                    List<JSONObject> roomList = new ArrayList<>();
                    for (Room room : server.roomList) {
                        sendObject = new JSONObject();
                        sendObject.put("roomId", room.getRoomId() + "");
                        sendObject.put("title", room.getTitle());
                        sendObject.put("members", room.getMembersName());
                        roomList.add(sendObject);
                    }
                    rooms.put("rooms", roomList);
                    out.writeUTF(rooms.toString());
                    out.flush();
                }
                if(type.equals("CSCreateRoom")){                                    // 방 만들기 완
                    if(!member.isJoin()){                                           // 이미 채팅방에 소속된 유저 사용제한
                        String title = jsonObject.getString("title");           
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
                }
                if(type.equals("CSJoinRoom")){                                      // 방 들어가기 완
                    if(!member.isJoin()){                                           // 이미 채팅방에 소속된 유저 사용제한
                        int roomId = jsonObject.getInt("roomId");
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
                }
                if(type.equals("CSLeaveRoom")){                                     // 방 나가기 완
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
                }
                if(type.equals("CSShutdown")){                                      // 서버 종료
                    
                }
            }
        } catch (SocketException e1) {
            System.out.println("상대방 연결이 종료되었습니다.");
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }

    
}
