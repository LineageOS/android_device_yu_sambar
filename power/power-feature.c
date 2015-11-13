/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <unistd.h>

#include <hardware/power.h>

#define LOG_TAG "Bacon_power_feature"
#include <utils/Log.h>

#define CONTROL_PATH "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/gesture_list"
#define CONTROL_EN_PATH "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/en_gesture"
#define ON_BITMASK 0x01
#define OFF_BITMASK 0x02
#define NODE_MAX 128

static int sysfs_read(char *path, char *s, int num_bytes)
{
    char buf[80];
    int count;
    int ret = 0;
    int fd = open(path, O_RDONLY);

    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);

        return -1;
    }

    if ((count = read(fd, s, num_bytes - 1)) < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error writing to %s: %s\n", path, buf);

        ret = -1;
    } else {
        s[count] = '\0';
    }

    close(fd);

    return ret;
}

static int sysfs_write(char *path, char *s)
{
    char buf[80];
    int len;
    int ret = 0;
    int fd = open(path, O_WRONLY);

    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);
        return -1 ;
    }

    len = write(fd, s, strlen(s));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error writing to %s: %s\n", path, buf);

        ret = -1;
    }

    close(fd);

    return ret;
}

static void enable_gestures()
{
    sysfs_write(CONTROL_EN_PATH, "1");
}

static void disable_gestures_if_all_off()
{
    /* example string: TAP 0a;UNLOCK0 0a;UNLOCK1 0a;LEFT 0a;RIGHT 0a;UP 0a;DOWN 0a;S_115_116 0a;o 0a;v 0a; */
    char tmp_str[NODE_MAX];
    char gesture_name[65];
    int chars;
    int state;
    char *pstr;

    if (sysfs_read(CONTROL_PATH, tmp_str, NODE_MAX - 1) <= 0)
        return;

    pstr = tmp_str;
    while (sscanf(pstr, "%64[^ ] %x;%n", gesture_name, &state, &chars) == 2) {
        pstr += chars;
        if ((state & OFF_BITMASK) != OFF_BITMASK)
            return;
    }

    sysfs_write(CONTROL_EN_PATH, "0");
}

void set_device_specific_feature(struct power_module *module __unused, feature_t feature, int state)
{
    char tmp_str[NODE_MAX];
    if (feature == POWER_FEATURE_DOUBLE_TAP_TO_WAKE) {
        if (state) {
            enable_gestures();
        }
        snprintf(tmp_str, NODE_MAX, "TAP %d;", state ? ON_BITMASK : OFF_BITMASK);
        sysfs_write(CONTROL_PATH, tmp_str);
        if (!state) {
            /* TODO this seperated node may have a race condition with the gestures settings */
            disable_gestures_if_all_off();
        }
    }
}

