#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <string.h>
#include <unistd.h>

#include <iostream>
#include <string>
#include <error.h>

using namespace std;

int main() {
    int s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s < 0) return 1;

    struct sockaddr_in sin;

    memset(&sin, 0, sizeof(sin));
    sin.sin_family = AF_INET;
    sin.sin_addr.s_addr = INADDR_ANY;
    sin.sin_port = htons(20000 + 115);
    if (bind(s, (struct sockaddr *) &sin, sizeof(sin)) < 0) {
        cerr << strerror(errno) << endl;
        return 0;
    }

    while(1){
        char buf2[65535] = "";
        memset(&sin, 0, sizeof(sin));
        socklen_t sin_size = sizeof(sin);
        int numBytes = recvfrom(s, buf2, sizeof(buf2), 0, (struct sockaddr *) &sin, &sin_size);
        cout << "Received: " << numBytes << endl;
        cout << "From " << inet_ntoa(sin.sin_addr) << ":" << ntohs(sin.sin_port) << endl;
        cout << "Received buf: " << buf2 << endl;

        // int port = ntohs(sin.sin_port);
        int port = htons(10002);

        string buf = buf2;
        memset(&sin, 0, sizeof(sin));
        sin.sin_family = AF_INET;
        sin.sin_addr.s_addr = inet_addr("127.0.0.1");
        sin.sin_port = port;
        numBytes = sendto(s, buf.c_str(), buf.length(), 0, (struct sockaddr *) &sin, sizeof(sin));
        if(numBytes == -1){
            perror("sendto");
        }
        
        cout << "Sent: " << numBytes << endl;

        memset(&sin, 0, sizeof(sin));
        sin_size = sizeof(sin);
        int result = getsockname(s, (struct sockaddr *) &sin, &sin_size);
        if (result == 0) {
            cout << "My addr: " << inet_ntoa(sin.sin_addr) << endl;
            cout << "My port: " << ntohs(sin.sin_port) << endl;
        }
    }
    close(s);
    return 0;
}
