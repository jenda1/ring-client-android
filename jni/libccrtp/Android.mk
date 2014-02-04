include $(CLEAR_VARS)

MY_LOCAL_PATH := libccrtp/sources
MY_OPENSSL := libopenssl
MY_COMMONCPP := libucommon/sources

LT_VERSION = 
LT_RELEASE = 
#SHARED_FLAGS = "-no-undefined"
SRTP_OPENSSL =
SRTP_GCRYPT =

#LOCAL_CPPFLAGS   += -std=gnu++0x -fexceptions -frtti

LOCAL_C_INCLUDES += $(MY_LOCAL_PATH)/src \
					$(MY_LOCAL_PATH) \
					$(MY_LOCAL_PATH)/src/ccrtp \
					$(MY_COMMONCPP)/inc \
					$(MY_COMMONCPP) \
		    		$(MY_OPENSSL)/include \
					$(MY_OPENSSL)

LOCAL_STATIC_LIBRARIES :=	libccgnu2 \
							libcrypto_static

LOCAL_MODULE     := libccrtp1

LOCAL_SHARED_LIBRARIES += libssl_shared

LOCAL_CPP_EXTENSION := .cxx .cpp

SRTP_SRC_O = 	$(MY_LOCAL_PATH)/src/ccrtp/crypto/openssl/hmac.cpp \
				$(MY_LOCAL_PATH)/src/ccrtp/crypto/openssl/SrtpSymCrypto.cpp

ifneq ($(SRTP_GCRYPT),)
SRTP_SRC_G =    $(MY_LOCAL_PATH)/src/ccrtp/crypto/gcrypt/gcrypthmac.cxx \
				$(MY_LOCAL_PATH)/src/ccrtp/crypto/gcrypt/gcryptSrtpSymCrypto.cpp \
				$(MY_LOCAL_PATH)/src/ccrtp/crypto/gcrypt/InitializeGcrypt.cpp
endif

SKEIN_SRCS = 	$(MY_LOCAL_PATH)/src/ccrtp/crypto/macSkein.cpp \
		        $(MY_LOCAL_PATH)/src/ccrtp/crypto/skein.c \
		        $(MY_LOCAL_PATH)/src/ccrtp/crypto/skein_block.c \
		        $(MY_LOCAL_PATH)/src/ccrtp/crypto/skeinApi.c

LOCAL_SRC_FILES  := $(MY_LOCAL_PATH)/src/rtppkt.cpp \
					$(MY_LOCAL_PATH)/src/rtcppkt.cpp \
					$(MY_LOCAL_PATH)/src/source.cpp \
					$(MY_LOCAL_PATH)/src/data.cpp \
					$(MY_LOCAL_PATH)/src/incqueue.cpp \
					$(MY_LOCAL_PATH)/src/outqueue.cpp \
					$(MY_LOCAL_PATH)/src/queue.cpp \
					$(MY_LOCAL_PATH)/src/control.cpp \
					$(MY_LOCAL_PATH)/src/members.cpp \
					$(MY_LOCAL_PATH)/src/socket.cpp \
					$(MY_LOCAL_PATH)/src/duplex.cpp \
					$(MY_LOCAL_PATH)/src/pool.cpp \
					$(MY_LOCAL_PATH)/src/CryptoContext.cpp \
					$(MY_LOCAL_PATH)/src/CryptoContextCtrl.cpp \
					$(SRTP_SRC_G) \
					$(SRTP_SRC_O) \
					$(SKEIN_SRCS)


#LOCAL_LDFLAGS    := -version-info $(LT_VERSION) -release $(LT_RELEASE) $(SHARED_FLAGS)

include $(BUILD_STATIC_LIBRARY)