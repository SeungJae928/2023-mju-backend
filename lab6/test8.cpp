#include <chrono>
#include <iostream>
#include <thread>
#include <mutex>

using namespace std;

int sum = 0;
mutex m;

void f() {
    for (int i = 0; i < 1000 * 1000 * 10; ++i) {
        unique_lock<mutex> ul(m);
        ++sum;
    }
}

int main() {
    thread t(f);
    for (int i = 0; i < 1000 * 1000 * 10; ++i) {
        m.lock();
        ++sum;
        m.unlock();
    }
    t.join();
    cout << "Sum: " << sum << endl;
}
