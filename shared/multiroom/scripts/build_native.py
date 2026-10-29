"""Build standalone Snapcast processes, separate from the AirPlay engine."""
from pathlib import Path
import subprocess,re,json,os,shutil,sys
root=Path(__file__).resolve().parents[2]/'multiroom-work';source=root/'snapcast';abi=sys.argv[1]
subprocess.run([sys.executable,str(Path(__file__).resolve().with_name('patch_group_timing.py'))],check=True)
assert abi in ('arm64-v8a','armeabi-v7a')
ndk=Path('/root/wpar/tools/android-ndk-r27c');boost=Path('/root/wpar/multiroom/deps/boost/usr/include')
assert (boost/'boost/version.hpp').exists()
work=Path('/root/wpar/multiroom/build')/abi;work.mkdir(parents=True,exist_ok=True)
def sources(name,var):
 text=(source/name/'CMakeLists.txt').read_text();body=re.search(r'set\('+var+r'\s+(.*?)\)',text,re.S).group(1)
 return [str(source/name/item) for line in body.splitlines() if not line.strip().startswith('#') for item in line.split()]
common=sources('common','SOURCES');client=sources('client','CLIENT_SOURCES');server=sources('server','SERVER_SOURCES')
client.append(str(source/'client/player/oboe_player.cpp'))
cmake='''cmake_minimum_required(VERSION 3.22)
project(panel_multiroom LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
add_compile_options(-O2 -ffunction-sections -fdata-sections -Wno-deprecated-declarations)
add_compile_definitions(BOOST_ERROR_CODE_HEADER_ONLY BOOST_ASIO_NO_TS_EXECUTORS BOOST_PROCESS_VERSION=1 BOOST_PROCESS_USE_STD_FS VERSION="0.34.0" REVISION="ee0f896cd02c474bf18c6cd9f10b0b7c8b6dd009")
'''
cmake+='include_directories("'+str(source)+'" "'+str(source/'client')+'" "'+str(source/'server')+'")\n'
cmake+='include_directories(SYSTEM "'+str(boost)+'")\n'
cmake+='include_directories("'+str(Path(__file__).resolve().parents[1]/'native-src')+'")\n'
cmake+='add_subdirectory("'+str(root/'oboe')+'" oboe-build)\n'
for name,files in [('groupcommon',common),('libwpar_groupclient.so',client),('libwpar_groupserver.so',server)]:
 cmake+=('add_library('+name+' STATIC' if name=='groupcommon' else 'add_executable('+name)+'\n'+'\n'.join('"'+item+'"' for item in files)+'\n)\n'
 if name!='groupcommon':cmake+='target_link_libraries('+name+' groupcommon log)\ntarget_link_options('+name+' PRIVATE -Wl,--gc-sections)\n'
cmake+='target_compile_definitions(libwpar_groupclient.so PRIVATE HAS_OBOE)\ntarget_link_libraries(libwpar_groupclient.so oboe dl)\n'
(work/'CMakeLists.txt').write_text(cmake)
env=dict(os.environ)
wrappers=Path('/root/wpar/multiroom/compiler');wrappers.mkdir(parents=True,exist_ok=True)
binary=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
for name in ('clang++','clang','ld.lld'):
    extra=(' -resource-dir='+str(binary.parent/'lib/clang/18')) if name in ('clang++','clang') else ''
    wrapper=wrappers/name;wrapper.write_text('#!/bin/sh\nexec /lib64/ld-linux-x86-64.so.2 "'+str(binary/name)+'"'+extra+' "$@"\n');wrapper.chmod(0o755)
target='aarch64-linux-android27' if abi=='arm64-v8a' else 'armv7a-linux-androideabi27'
toolchain=work/'toolchain.cmake'
toolchain.write_text('set(CMAKE_SYSTEM_NAME Linux)\nset(CMAKE_CXX_COMPILER "'+str(wrappers/'clang++')+'")\nset(CMAKE_CXX_COMPILER_TARGET '+target+')\nset(CMAKE_CXX_FLAGS_INIT "--sysroot='+str(ndk/'toolchains/llvm/prebuilt/linux-x86_64/sysroot')+' -stdlib=libc++")\nset(CMAKE_EXE_LINKER_FLAGS_INIT "-static-libstdc++ --ld-path='+str(wrappers/'ld.lld')+'")\n')
with toolchain.open('a') as config:config.write('set(CMAKE_C_COMPILER "'+str(wrappers/'clang')+'")\nset(CMAKE_C_COMPILER_TARGET '+target+')\nset(CMAKE_C_FLAGS_INIT "--sysroot='+str(ndk/'toolchains/llvm/prebuilt/linux-x86_64/sysroot')+'")\n')
subprocess.run(['cmake','--fresh','-S',str(work),'-B',str(work/'out'),'-DCMAKE_TOOLCHAIN_FILE='+str(toolchain),'-DCMAKE_BUILD_TYPE=Release'],check=True,env=env)
subprocess.run(['cmake','--build',str(work/'out'),'-j2'],check=True,env=env)
dest=root/'native'/abi;dest.mkdir(parents=True,exist_ok=True)
for name in ('libwpar_groupclient.so','libwpar_groupserver.so'):shutil.copyfile(work/'out'/name,dest/name)
print('Built separate grouping processes for '+abi)
