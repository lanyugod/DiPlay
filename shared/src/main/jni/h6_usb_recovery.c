#include <jni.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

/* Do not claim/release here: Android releaseInterface can rebind the kernel driver. */
JNIEXPORT jint JNICALL Java_com_shilapi_xcertplay_transport_H6UsbRecoveryNative_disconnect(
        JNIEnv *env, jobject self, jint fd, jint interface_id) {
    (void) env;
    (void) self;
    if (fd < 0 || interface_id < 0 || interface_id > 255) return EINVAL;
    struct usbdevfs_ioctl request = {
        .ifno = interface_id,
        .ioctl_code = USBDEVFS_DISCONNECT,
        .data = NULL
    };
    int result;
    do { result = ioctl(fd, USBDEVFS_IOCTL, &request); } while (result < 0 && errno == EINTR);
    return result < 0 ? errno : 0;
}
