cmake_minimum_required(VERSION 3.21)
project(ujima-files LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.4 REQUIRED COMPONENTS Core Gui Qml Quick Network Svg)

add_executable(ujima-files
  src/main.cpp
  src/glyphs.h   src/glyphs.cpp
  src/places.h   src/places.cpp
  src/dirmodel.h src/dirmodel.cpp
  src/files.h    src/files.cpp
  src/prefs.h)

target_link_libraries(ujima-files PRIVATE Qt6::Core Qt6::Gui Qt6::Qml Qt6::Quick Qt6::Network Qt6::Svg)
