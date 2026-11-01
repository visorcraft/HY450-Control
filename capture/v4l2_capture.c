#include <errno.h>
#include <fcntl.h>
#include <linux/videodev2.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

struct buffer {
    void *data;
    size_t length;
};

static int xioctl(int fd, unsigned long request, void *argument) {
    int result;
    do {
        result = ioctl(fd, request, argument);
    } while (result < 0 && errno == EINTR);
    return result;
}

static void fourcc_string(uint32_t value, char output[5]) {
    output[0] = (char)(value & 0xff);
    output[1] = (char)((value >> 8) & 0xff);
    output[2] = (char)((value >> 16) & 0xff);
    output[3] = (char)((value >> 24) & 0xff);
    output[4] = '\0';
}

static int probe(int fd) {
    struct v4l2_capability capability;
    memset(&capability, 0, sizeof(capability));
    if (xioctl(fd, VIDIOC_QUERYCAP, &capability) < 0) {
        perror("VIDIOC_QUERYCAP");
        return 1;
    }
    printf("driver=%s card=%s bus=%s capabilities=0x%08x device_caps=0x%08x\n",
            (char *)capability.driver, (char *)capability.card,
            (char *)capability.bus_info, capability.capabilities,
            capability.device_caps);
    struct v4l2_format current;
    memset(&current, 0, sizeof(current));
    current.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (xioctl(fd, VIDIOC_G_FMT, &current) == 0) {
        char name[5];
        fourcc_string(current.fmt.pix.pixelformat, name);
        printf("current=%ux%u fourcc=%s stride=%u\n", current.fmt.pix.width,
                current.fmt.pix.height, name, current.fmt.pix.bytesperline);
    }
    for (uint32_t index = 0;; index++) {
        struct v4l2_fmtdesc format;
        memset(&format, 0, sizeof(format));
        format.index = index;
        format.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        if (xioctl(fd, VIDIOC_ENUM_FMT, &format) < 0) {
            if (errno == EINVAL) break;
            perror("VIDIOC_ENUM_FMT");
            return 1;
        }
        char name[5];
        fourcc_string(format.pixelformat, name);
        printf("format[%u]=%s description=%s\n", index, name,
                (char *)format.description);
    }
    return 0;
}

static int set_format(int fd, struct v4l2_format *format, uint32_t pixel_format) {
    memset(format, 0, sizeof(*format));
    format->type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    format->fmt.pix.width = 640;
    format->fmt.pix.height = 480;
    format->fmt.pix.pixelformat = pixel_format;
    format->fmt.pix.field = V4L2_FIELD_ANY;
    if (xioctl(fd, VIDIOC_S_FMT, format) < 0) return -1;
    return format->fmt.pix.pixelformat == pixel_format ? 0 : -1;
}

static int write_all(int fd, const unsigned char *data, size_t length) {
    size_t offset = 0;
    while (offset < length) {
        ssize_t count = write(fd, data + offset, length - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return -1;
        offset += (size_t)count;
    }
    return 0;
}

static int capture(int fd, const char *raw_path, const char *metadata_path) {
    struct v4l2_format format;
    uint32_t selected = V4L2_PIX_FMT_MJPEG;
    if (set_format(fd, &format, selected) < 0) {
        selected = V4L2_PIX_FMT_YUYV;
        if (set_format(fd, &format, selected) < 0) {
            fprintf(stderr, "Neither MJPEG nor YUYV capture is available\n");
            return 1;
        }
    }
    char name[5];
    fourcc_string(selected, name);
    printf("capture=%ux%u fourcc=%s stride=%u\n", format.fmt.pix.width,
            format.fmt.pix.height, name, format.fmt.pix.bytesperline);

    struct v4l2_requestbuffers request;
    memset(&request, 0, sizeof(request));
    request.count = 2;
    request.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    request.memory = V4L2_MEMORY_MMAP;
    if (xioctl(fd, VIDIOC_REQBUFS, &request) < 0 || request.count < 2) {
        perror("VIDIOC_REQBUFS");
        return 1;
    }
    struct buffer buffers[2];
    memset(buffers, 0, sizeof(buffers));
    for (uint32_t index = 0; index < 2; index++) {
        struct v4l2_buffer buffer;
        memset(&buffer, 0, sizeof(buffer));
        buffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buffer.memory = V4L2_MEMORY_MMAP;
        buffer.index = index;
        if (xioctl(fd, VIDIOC_QUERYBUF, &buffer) < 0) {
            perror("VIDIOC_QUERYBUF");
            return 1;
        }
        buffers[index].length = buffer.length;
        buffers[index].data = mmap(NULL, buffer.length, PROT_READ | PROT_WRITE,
                MAP_SHARED, fd, buffer.m.offset);
        if (buffers[index].data == MAP_FAILED) {
            perror("mmap");
            return 1;
        }
    }
    for (uint32_t index = 0; index < 2; index++) {
        struct v4l2_buffer buffer;
        memset(&buffer, 0, sizeof(buffer));
        buffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buffer.memory = V4L2_MEMORY_MMAP;
        buffer.index = index;
        if (xioctl(fd, VIDIOC_QBUF, &buffer) < 0) {
            perror("VIDIOC_QBUF");
            return 1;
        }
    }
    enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (xioctl(fd, VIDIOC_STREAMON, &type) < 0) {
        perror("VIDIOC_STREAMON");
        return 1;
    }
    fd_set readers;
    FD_ZERO(&readers);
    FD_SET(fd, &readers);
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    int ready = select(fd + 1, &readers, NULL, NULL, &timeout);
    if (ready <= 0) {
        fprintf(stderr, "Frame wait timed out or failed\n");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    struct v4l2_buffer frame;
    memset(&frame, 0, sizeof(frame));
    frame.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    frame.memory = V4L2_MEMORY_MMAP;
    if (xioctl(fd, VIDIOC_DQBUF, &frame) < 0) {
        perror("VIDIOC_DQBUF");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    if (frame.index >= 2) {
        fprintf(stderr, "Invalid returned buffer index\n");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    int raw = open(raw_path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (raw < 0) {
        perror("open raw output");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    size_t length = frame.bytesused;
    if (length == 0 || length > buffers[frame.index].length) {
        fprintf(stderr, "Invalid frame length\n");
        close(raw);
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    int result = write_all(raw, buffers[frame.index].data, length);
    if (close(raw) < 0 || result < 0) {
        perror("write raw output");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    FILE *metadata = fopen(metadata_path, "w");
    if (!metadata) {
        perror("open metadata output");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    fprintf(metadata, "width=%u\nheight=%u\nfourcc=%s\nstride=%u\n",
            format.fmt.pix.width, format.fmt.pix.height, name,
            format.fmt.pix.bytesperline);
    if (fclose(metadata) != 0) {
        perror("write metadata output");
        xioctl(fd, VIDIOC_STREAMOFF, &type);
        return 1;
    }
    if (xioctl(fd, VIDIOC_STREAMOFF, &type) < 0) {
        perror("VIDIOC_STREAMOFF");
        return 1;
    }
    for (uint32_t index = 0; index < 2; index++) {
        munmap(buffers[index].data, buffers[index].length);
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <device> probe | %s <device> capture <raw> <metadata>\n",
                argv[0], argv[0]);
        return 2;
    }
    int fd = open(argv[1], O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        perror("open video device");
        return 1;
    }
    int result;
    if (strcmp(argv[2], "probe") == 0 && argc == 3) {
        result = probe(fd);
    } else if (strcmp(argv[2], "capture") == 0 && argc == 5) {
        result = capture(fd, argv[3], argv[4]);
    } else {
        fprintf(stderr, "Invalid arguments\n");
        result = 2;
    }
    close(fd);
    return result;
}
