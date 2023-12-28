set PATH=%PATH%;D:\Clion\bin\mingw\bin
cd bin
g++ -O3 -DNDEBU -shared -o libcpp.dll -Wl,--out-implib,libcpp.dll.a -Wl,--major-image-version,0,--minor-image-version,0 CMakeFiles/cpp.dir/library.cpp.obj  -lkernel32 -luser32 -lgdi32 -lwinspool -lshell32 -lole32 -loleaut32 -luuid -lcomdlg32 -ladvapi32 -lbcrypt -lwsock32
cd ..
pause