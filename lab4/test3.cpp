#include <fstream>
#include <string>
#include <iostream>

#include "person.pb.h"

using namespace std;
using namespace mju;

int main(){
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
    cout << "Length : " << s.length() << endl;
    cout << s << endl;
}