/*
 * Copyright (C) 2011, Jeff Moyer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>

#include <CapSense.h>
#include <Roomba.h>

/*
 * This information has to be the same in the Android App.
 */
AndroidAccessory acc("jmoyer",
		     "iRobot",
		     "iRobot Control Board",
		     "1.0",
		     "http://code.google.com/",
		     "0000000012345678");
/*
 * For some bizarre reason, a CapSense isntance needs to be present in
 * order for ANYTHING to work (including Serial.print).  Ordering also
 * seems to matter.  I think it has to be the last static class
 * instantiation.
 */
#define TOUCH_RECV 14
#define TOUCH_SEND 15
Roomba roomba(&Serial2);
static Roomba::Mode mode = Roomba::ModeSafe;
CapSense touch_robot = CapSense(TOUCH_SEND, TOUCH_RECV);

uint8_t intro_song[] = {
  60, 8,
  61, 8,
  62, 8,
  63, 8,
  64, 8,
};
uint8_t plugin_song[] = {
  60, 8,
  64, 8,
};
uint8_t unplug_song[] = {
  64, 8,
  60, 8,
};
uint8_t whistle_song[] = {
  79, 8,
  81, 8,
  79, 16,
  77, 16,
  76, 16,
  77, 16,
  79, 64,
};
#define INTRO_SONG 0
#define PLUGIN_SONG 1
#define UNPLUG_SONG 2
#define WHISTLE_SONG 3

static int started = 0;
static bool was_connected = false;

void setup();
void loop();

void startRoomba()
{
        started = 1;
        Serial.print("Starting Roomba!\n");
        roomba.start();
        roomba.safeMode();
        mode = Roomba::ModeSafe;
        roomba.song(INTRO_SONG, intro_song, sizeof(intro_song));
        roomba.song(PLUGIN_SONG, plugin_song, sizeof(plugin_song));
        roomba.song(UNPLUG_SONG, unplug_song, sizeof(unplug_song));
        roomba.song(WHISTLE_SONG, whistle_song, sizeof(whistle_song));
        roomba.playSong(INTRO_SONG);
}

void init_roomba()
{
        startRoomba();
}

void setup()
{
        Serial.begin(115200);
        Serial.print("Start\n");
        init_roomba();
        Serial.print("acc.powerOn\n");
	acc.powerOn();
        Serial.print("poweron done\n");
}

void check_mode()
{
        uint8_t _mode;
        roomba.getSensors(Roomba::SensorOIMode, &_mode, (uint8_t)1);
        if (_mode == Roomba::ModePassive) {
            roomba.safeMode();
            mode = Roomba::ModeSafe;
        }
}

// you can't send sensor commands to the create more often
// than every 15 ms.
#define ROOMBA_MIN_SENSOR_DELAY 15

void loop()
{
        int i = 0;
        int ready;
        char buf[128];
        static int nr_loops = 0;
  	byte msg[5];
        static bool driving = false;
        static bool bumped = false;
        unsigned long time_now;
        static unsigned long lastSensorRead = 0;
        static unsigned long driving_ts = 0;
        static bool demo_running = false;

	if (acc.isConnected()) {
		int len;

                time_now = millis();
                if (lastSensorRead - time_now > ROOMBA_MIN_SENSOR_DELAY) {
                        uint8_t packetIDs[2];
                        uint8_t sensorData[3];
                        bool ret;
                        lastSensorRead = time_now;
                        short distance = 0;

                        packetIDs[0] = Roomba::SensorBumpsAndWheelDrops;
                        packetIDs[1] = Roomba::SensorDistance;
                        ret = roomba.getSensorsList(&packetIDs[0], (uint8_t)2, &sensorData[0],
                                                    (uint8_t)3);
                        if (ret == false)
                                Serial.print("error reading sensors!\n");
                        else {
                                // stop on bump
                                if (sensorData[0] & 0x3) {
                                        if (!bumped) {
                                          roomba.drive(0, Roomba::DriveStraight);
                                          driving = false;
                                          bumped = true;
                                        }
                                } else if (bumped)
                                        bumped = false;
                                // if we're supposed to be moving, make sure we are!
                                distance += sensorData[1]<<8 | sensorData[2];
                                if (driving && (millis() - driving_ts) > 300) {
                                  if (distance == 0 && !bumped) {
                                    roomba.drive(0, Roomba::DriveStraight);
                                    driving = false;
                                  }
                                  distance = 0;
                                }
                        }
                }

                len = acc.read(msg, sizeof(msg), 1);
		if (len > 0) {
                        check_mode();
			// assumes only one command per packet
                        switch (msg[0]) {
                        case 'd':
                                Serial.print("Drive!\n");
                                if (msg[1] || msg[2]) {
                                    driving = true;
                                    driving_ts = millis();
                                }
                                if (demo_running) {
                                    roomba.demo(Roomba::DemoAbort);
                                    demo_running = false;
                                }
                                if (mode == Roomba::ModePassive) {
                                    roomba.safeMode();
                                    mode = Roomba::ModeSafe;
                                }
                                roomba.drive((msg[1] << 8) | msg[2],
                                             (msg[3] << 8) | msg[4]);
                                break;
                        case 'D': // Demo
                                Serial.print("Demo!\n");
                                driving = false;
                                demo_running = true;
                                mode = Roomba::ModePassive;
                                roomba.demo((Roomba::Demo)msg[1]);
                                break;
                        case 's': // Song
                                Serial.print("Whistle while you work\n");
                                roomba.playSong(WHISTLE_SONG);
                                break;
                        default:
                                Serial.print("bad command.\n");
                                if (!demo_running) {
                                    roomba.drive(0, Roomba::DriveStraight);
                                    driving = false;
                                }
                                break;
                        }
		}

                if (!was_connected) {
                  roomba.playSong(PLUGIN_SONG);
                  was_connected = true;
                }
	} else if (was_connected) {
                was_connected = false;
                if (driving) {
                        driving = false;
                        roomba.drive(0, Roomba::DriveStraight);
                }
                roomba.playSong(UNPLUG_SONG);
	}
        delay(10);
}
