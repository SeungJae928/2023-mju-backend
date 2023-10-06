#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <string.h>
#include <unistd.h>

#include "person.pb.h"
#include <iostream>
#include <string>

using namespace std;
using namespace mju;

const string serialize();
void deserialize(char* str);

int main() {
    int s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s < 0) return 1;

    const string se = serialize();
    
    string buf = "Hello World1";

    struct sockaddr_in sin;

    memset(&sin, 0, sizeof(sin));
    sin.sin_family = AF_INET;
    sin.sin_port = htons(10001);
    sin.sin_addr.s_addr = inet_addr("127.0.0.1");

    int numBytes = sendto(s, se.c_str(), se.length(), 0, (struct sockaddr *) &sin, sizeof(sin));
    cout << "Sent: " << numBytes << endl;

    char se2[65536];
    memset(&sin, 0, sizeof(sin));
    socklen_t sin_size = sizeof(sin);
    numBytes = recvfrom(s, se2, sizeof(se2), 0, (struct sockaddr *) &sin, &sin_size);
    cout << "Recevied: " << numBytes << endl;
    cout << "From " << inet_ntoa(sin.sin_addr) << endl;

    deserialize(se2);

    close(s);
    return 0;
}

const string serialize(){
    Person *p = new Person;
    p -> set_name("Seungjae");
    p -> set_id(12345678);

    Person::PhoneNumber* phone = p -> add_phones();
    phone -> set_number("010-0000-0000");
    phone -> set_type(Person::MOBILE);

    phone = p -> add_phones();
    phone -> set_number("032-000-0000");
    phone -> set_type(Person::HOME);

    const string s = p -> SerializeAsString();

    return s;
}


void deserialize(char* str){
    Person *p2 = new Person;
    p2 -> ParseFromString(str);
    cout << "Name : " << p2 -> name() << endl;
    cout << "ID : " << p2 -> id() << endl;
    for(int i = 0; i < p2->phones_size(); i++){
        cout << "Type : " << p2->phones(i).type() << endl;
        cout << "Phone : " << p2->phones(i).number() << endl;
    }
}