#FROM resin/rpi-raspbian:jessie
FROM resin/raspberrypi2-debian:jessie
#FROM resin/amd64-debian:jessie

# Install wget and curl
RUN apt-get clean && apt-get update && apt-get install -y \
  wget \
  curl

# prevent httpredir from doing nasty things
#RUN sed -i "s/httpredir.debian.org/`curl -s -D - http://httpredir.debian.org/demo/debian/ | awk '/^Link:/ { print $2 }' | sed -e 's@<http://\(.*\)/debian/>;@\1@g'`/" /etc/apt/sources.list

# Add the key for foundation repository
# RUN wget http://archive.raspberrypi.org/debian/raspberrypi.gpg.key -O - | sudo apt-key add -

# Add apt source of the foundation repository
# We need this source because bluez needs to be patched in order to work with rpi3 ( Issue #1314: How to get BT working on Pi3B. by clivem in raspberrypi/linux on GitHub )
# Add it on top so apt will pick up packages from there
# RUN sed -i '1s#^#deb http://archive.raspberrypi.org/debian jessie main\n#' /etc/apt/sources.list

# Install Java
# with sources from https://launchpad.net/~webupd8team/+archive/ubuntu/java
# using the fix described at http://www.all4pages.com/2014/03/23/wie-installieren-wir-oracle-java-8-auf-wheezy-ueber-die-debian-sourcelist/
RUN echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list && \
    echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list && \
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys EEA14886 && \
    echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
    apt-get update && \
    apt-get install -y oracle-java8-installer --no-install-recommends && \
    apt-get clean && \
    apt-get purge && \
    rm -rf /var/lib/apt/lists/*

# Install Java on Raspbian
#RUN apt-get install oracle-java8-jdk

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
#ENV JAVA_HOME $(readlink -f /usr/bin/javac | sed "s:/bin/javac::")
#ENV JAVA_HOME /usr/lib/jvm/jdk-8-oracle-arm32-vfp-hflt

# Add packages
RUN apt-get update && apt-get install --no-install-recommends -y \
    build-essential \
    git\
    ca-certificates \
    apt \
    software-properties-common \
    unzip \
    cpp \
    binutils \
    maven \
    gettext \
    Xvfb \
    libc6-dev \
    make \
    cmake \
    cmake-data \
    pkg-config \
    clang \
    gcc-4.9 \
    g++-4.9 \
    glib2.0 \
    libglib2.0-0 \
    qdbus


RUN apt-get update && apt-get install --no-install-recommends -y \
    libbluetooth-dev \
    libudev-dev



# These env vars enable sync_mode on all devices.
#ENV SYNC_MODE=on
#ENV INITSYSTEM=on

# resin-sync will always sync to /usr/src/app, so code needs to be here.
WORKDIR /usr/src/app
ENV APATH /usr/src/app

COPY scripts scripts

RUN CC=clang CXX=clang++ CMAKE_C_COMPILER=clang CMAKE_CXX_COMPILER=clang++ \
scripts/install-dbus-java.sh $APATH/deps

RUN PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:/usr/lib/pkgconfig CC=clang CXX=clang++ CMAKE_C_COMPILER=clang CMAKE_CXX_COMPILER=clang++ \
scripts/install-tinyb.sh $APATH/deps

# we need dbus-launch
RUN apt-get update && apt-get install --no-install-recommends -y \
    dbus-x11

# required by tinyb JNI
RUN apt-get update && apt-get install --no-install-recommends -y \
    libxrender1

# required by bluez
RUN apt-get update && apt-get install --no-install-recommends -y \
    libglib2.0-dev \
    libdbus-1-dev \
    libudev-dev \
    libical-dev \
    libreadline-dev

# isntall bluez
RUN wget http://www.kernel.org/pub/linux/bluetooth/bluez-5.39.tar.xz \
    && tar xf bluez-5.39.tar.xz \
    && rm bluez-5.39.tar.xz \
    && cd bluez-5.39 \
    && ./configure \
    && make -j 4 \
    && sudo make install \
    && cd .. && rm -rf bluez-5.39

# copy directories into WORKDIR
COPY agile-interfaces agile-interfaces
COPY agile-main agile-main
COPY iot.agile.DeviceManager iot.agile.DeviceManager
COPY iot.agile.ProtocolManager iot.agile.ProtocolManager
COPY iot.agile.http iot.agile.http
COPY iot.agile.protocol.BLE iot.agile.protocol.BLE
COPY test test
COPY pom.xml pom.xml

RUN mvn clean install -U


CMD [ "bash", "/usr/src/app/scripts/start.sh" ]
