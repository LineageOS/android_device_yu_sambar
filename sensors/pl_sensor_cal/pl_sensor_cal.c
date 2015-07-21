/*
 * Copyright (C) 2015 The CyanogenMod Project <http://www.cyanogenmod.org>
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
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

#define SENSOR_CAL_P		"/persist/P_sensor_data.bin"
#define SENSOR_CAL_L		"/persist/L_sensor_data.bin"

#define SENSOR_SYSFS "/sys/ps/link/driver/cali"

struct ps_cal {
	int high_thd;
	int low_thd;
	int cover;
	int result;
};

struct ls_cal {
	int value;
	int test_adc;
	int value_cal;
	int transmittance;
	int value_cal_adc;
};

int read_data(const char *file, char *buffer, size_t len)
{
	int ret;
	int fd;

	fd = open(file, O_RDONLY);
	if (fd < 0) {
		printf("Unable to open %s: %s\n", file, strerror(errno));
		return -1;
	}

	ret = read(fd, buffer, len);
	if (ret < 0) {
		printf("Unable to read %s: %s\n", file, strerror(errno));
		close(fd);
		return -1;
	}

	close(fd);
	return 0;
}

int read_proximity_calibration(struct ps_cal* cal)
{
	int ret;
	char buffer[50];

	ret = read_data(SENSOR_CAL_P, buffer, sizeof(buffer));
	if (ret < 0) {
		return -1;
	}

	ret = sscanf(buffer, "%d %d %d %d",
		&cal->high_thd,
		&cal->low_thd,
		&cal->result,
		&cal->cover);

	if (ret != 4) {
		printf("Unable to parse proximity calibration\n");
		return -1;
	}

	return 0;
}

int read_light_calibration(struct ls_cal* cal)
{
	int ret;
	char buffer[50];

	ret = read_data(SENSOR_CAL_L, buffer, sizeof(buffer));
	if (ret < 0) {
		return -1;
	}

	ret = sscanf(buffer, "%d %d %d %d %d",
		&cal->value, &cal->test_adc,
		&cal->value_cal,
		&cal->transmittance,
		&cal->value_cal_adc);

	if (ret != 5) {
		printf("Unable to parse light calibration\n");
		return -1;
	}

	return 0;
}

int print_proximity_calibration()
{
	struct ps_cal calibration;
	if (read_proximity_calibration(&calibration))
		return -1;

	printf("Promixity:\n");
	printf(" high_thd: %d\n", calibration.high_thd);
	printf(" low_thd: %d\n", calibration.low_thd);
	printf(" cover: %d\n", calibration.cover);
	printf(" result: %d\n", calibration.result);
	return 0;
}

int print_light_calibration()
{
	struct ls_cal calibration;
	if (read_light_calibration(&calibration))
		return -1;

	printf("Light:\n");
	printf(" value: %d\n", calibration.value);
	printf(" test_adc: %d\n", calibration.test_adc);
	printf(" value_cal: %d\n", calibration.value_cal);
	printf(" transmittance: %d\n", calibration.transmittance);
	printf(" value_cal_adc: %d\n", calibration.value_cal_adc);
	return 0;
}

int print_calibration()
{
	int ret = 0;
	ret |= print_proximity_calibration();
	ret |= print_light_calibration();
	return ret;
}

int write_calibration()
{
	char buffer[50];
	struct ps_cal ps_calibration;
	struct ls_cal ls_calibration;
	int ret;
	int fd;

	if (read_light_calibration(&ls_calibration) ||
		read_proximity_calibration(&ps_calibration)) {
		return -1;
	}

	ret = sprintf(buffer, "%x %x %x %x %x %x %x %x %x\n",
		ps_calibration.high_thd,
		ps_calibration.low_thd,
		ps_calibration.result,
		ps_calibration.cover,
		ls_calibration.value,
		ls_calibration.test_adc,
		ls_calibration.value_cal,
		ls_calibration.transmittance,
		ls_calibration.value_cal_adc);

	if (ps_calibration.high_thd == 0 || ps_calibration.low_thd == 0) {
		printf("Invalid calibration data\n");
		return -1;
	}

	fd = open(SENSOR_SYSFS, O_RDWR);
	if (fd < 0) {
		printf("Unable to open %s: %s\n", SENSOR_SYSFS, strerror(errno));
		return -1;
	}

	ret = write(fd, buffer, ret);
	if (ret < 0) {
		printf("Unable to write calibration: %s\n", strerror(errno));
	}
	close(fd);
	return ret > 0;
}

int main(int argc, char **argv)
{
	if (argc > 1) {
		char *option = argv[1];
		if (strcmp("-p", option)) {
			printf("Invalid option %s\n", option);
			return -1;
		}

		return print_calibration();
	}

	return write_calibration();
}