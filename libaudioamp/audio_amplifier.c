/*
 * Copyright (C) 2015, The CyanogenMod Project
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

#define LOG_TAG "libaudioamp"
#include <cutils/log.h>
#include <system/audio.h>
#include <tinyalsa/asoundlib.h>
#include <tinycompress/tinycompress.h>
#include <msm8974/platform.h>
#include <audio_hw.h>
#include "audio_amplifier.h"


enum tfa9887_Audio_Mode
{
    Audio_Mode_Music_Normal = 0,
    Audio_Mode_Voice_NB,
    Audio_Mode_Voice_NB_EXTRA,
    Audio_Mode_Voice_WB,
    Audio_Mode_Voice_WB_EXTRA,
    Audio_Mode_VT_NB,
    Audio_Mode_VT_WB,
    Audio_Mode_Voice_VOIP,
    Audio_Mode_Voice_VoLTE,
    Audio_Mode_Voice_VoLTE_EXTRA,
    Audio_Mode_VT_VoLTE,
};

extern void tfa9887_init();
extern int tfa9887_speakeron(int mode, int first);
extern int tfa9887_speakeroff();
extern int tfa9887_calibration();

static int open_count = 0;
static int num_streams = 0;
static bool speaker_on = false;
static bool calibrating = true;
static bool writing = false;
static int devices = SND_DEVICE_NONE;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

static int quat_mi2s_interface_en(bool enable)
{
    enum mixer_ctl_type type;
    struct mixer_ctl *ctl;
    struct mixer *mixer = mixer_open(0);

    if (mixer == NULL) {
        ALOGE("Error opening mixer 0");
        return -1;
    }

    ctl = mixer_get_ctl_by_name(mixer, "QUAT_MI2S_RX Audio Mixer MultiMedia1");
    if (ctl == NULL) {
        mixer_close(mixer);
        ALOGE("Could not find QUAT_MI2S_RX Audio Mixer MultiMedia1");
        return -1;
    }

    type = mixer_ctl_get_type(ctl);
    if (type != MIXER_CTL_TYPE_BOOL) {
        ALOGE("QUAT_MI2S_RX Audio Mixer MultiMedia1 is not supported");
        mixer_close(mixer);
        return -1;
    }

    mixer_ctl_set_value(ctl, 0, enable);
    mixer_close(mixer);
    return 0;
}

void *write_dummy_data(void *param __attribute__ ((unused)))
{
    char *buffer;
    int size;
    struct pcm *pcm;
    struct pcm_config config;

    config.channels = 2;
    config.rate = 48000;
    config.period_size = 256;
    config.period_count = 2;
    config.format = PCM_FORMAT_S16_LE;
    config.start_threshold = config.period_size * config.period_count - 1;
    config.stop_threshold = config.period_size * config.period_count;
    config.silence_threshold = 0;
    config.avail_min = 1;

    if (quat_mi2s_interface_en(true)) {
        ALOGE("Failed to enable QUAT_MI2S_RX Audio Mixer MultiMedia1");
        return NULL;
    }

    pcm = pcm_open(0, 0, PCM_OUT | PCM_MONOTONIC, &config);
    if (!pcm || !pcm_is_ready(pcm)) {
        ALOGE("pcm_open failed: %s", pcm_get_error(pcm));
        if (pcm) {
            goto err_close_pcm;
        }
        goto err_disable_quat;
    }

    size = DEEP_BUFFER_OUTPUT_PERIOD_SIZE * 8;
    buffer = calloc(size, 1);
    if (!buffer) {
        ALOGE("failed to allocate buffer");
        goto err_close_pcm;
    }

    do {
        if (pcm_write(pcm, buffer, size)) {
            ALOGE("pcm_write failed");
        }
        pthread_mutex_lock(&mutex);
        writing = true;
        pthread_cond_signal(&cond);
        pthread_mutex_unlock(&mutex);
    } while (calibrating);

err_free:
    free(buffer);
err_close_pcm:
    pcm_close(pcm);
err_disable_quat:
    quat_mi2s_interface_en(false);
    return NULL;
}

int amplifier_open(void)
{
    ALOGV("%s:%d", __func__, __LINE__);
    if (!open_count) {
        pthread_t write_thread;
        calibrating = true;
        pthread_create(&write_thread, NULL, write_dummy_data, NULL);
        pthread_mutex_lock(&mutex);
        while(!writing) {
            pthread_cond_wait(&cond, &mutex);
        }
        pthread_mutex_unlock(&mutex);
        tfa9887_calibration();
        calibrating = false;
        pthread_join(write_thread, NULL);
    }
    open_count++;
    ALOGI("%s:%d use count: %d", __func__, __LINE__, open_count);
    return 0;
}

bool is_amplifier_device()
{
    switch(devices) {
    case SND_DEVICE_OUT_SPEAKER:
    case SND_DEVICE_OUT_SPEAKER_REVERSE:
    case SND_DEVICE_OUT_SPEAKER_AND_HEADPHONES:
    case SND_DEVICE_OUT_SPEAKER_AND_HDMI:
    case SND_DEVICE_OUT_SPEAKER_AND_USB_HEADSET:
    case SND_DEVICE_OUT_SPEAKER_AND_ANC_HEADSET:
    case SND_DEVICE_OUT_SPEAKER_PROTECTED:
    case SND_DEVICE_OUT_VOICE_SPEAKER:
    case SND_DEVICE_OUT_VOICE_SPEAKER_PROTECTED:
        return true;
    default:
        return false;
    }
}

void amplifier_stream_start(struct audio_stream_out *stream, bool offload)
{
    char *buffer;
    int size;
    struct stream_out *out = (struct stream_out *)stream;
    if (!is_amplifier_device())
        return;

    if (num_streams == 0) {
        size = pcm_frames_to_bytes(out->pcm, pcm_get_buffer_size(out->pcm));
        buffer = calloc(size, 1);
        if (!buffer) {
            return;
        }

        if (offload) {
            compress_write(out->compr, buffer, size);
        } else {
            if (out->usecase == USECASE_AUDIO_PLAYBACK_AFE_PROXY)
                pcm_mmap_write(out->pcm, buffer, size);
            else
                pcm_write(out->pcm, buffer, size);
        }

        tfa9887_speakeron(Audio_Mode_Music_Normal, 0);
        free(buffer);
    }

    num_streams++;
}

void amplifier_stream_standby(struct audio_stream_out *stream __attribute__((unused)))
{
    if (!is_amplifier_device())
        return;

    num_streams--;
    if (num_streams == 0) {
        tfa9887_speakeroff();
    }
}

void amplifier_set_devices(int devs)
{
    ALOGI("%s:%d device: %d", __func__, __LINE__, devices);

    if (devices >= SND_DEVICE_IN_BEGIN && devices < SND_DEVICE_IN_END)
        return;

    devices = devs;
}

int amplifier_set_mode(audio_mode_t mode __attribute__((unused)))
{
    ALOGI("%s:%d", __func__, __LINE__);
    return 0;
}

int amplifier_close(void)
{
    ALOGI("%s:%d", __func__, __LINE__);
    if (open_count == 0) {
        ALOGE("amplifier is not open");
        return 0;
    }

    open_count--;
    if (open_count == 0) {
        //TODO: ?
    }
    ALOGI("%s:%d use count: %d", __func__, __LINE__, open_count);
    return 0;
}
