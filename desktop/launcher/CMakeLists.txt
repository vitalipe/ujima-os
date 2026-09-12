cmake_minimum_required(VERSION 3.21)
project(ujima-launcher LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.4 REQUIRED COMPONENTS Core Gui Qml Quick Network)

add_executable(ujima-launcher
  src/main.cpp
  src/stream.h  src/stream.cpp
  src/desktop.h src/desktop.cpp)

target_link_libraries(ujima-launcher PRIVATE Qt6::Core Qt6::Gui Qt6::Qml Qt6::Quick Qt6::Network)
