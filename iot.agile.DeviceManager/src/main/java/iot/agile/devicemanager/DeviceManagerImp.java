/*
 * Copyright 2016 Dagmawi Neway Mekuria <d.mekuria@create-net.org>.
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
package iot.agile.devicemanager;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iot.agile.Device;
import iot.agile.DeviceManager;
import iot.agile.devicemanager.device.MedicalDevice;
import iot.agile.devicemanager.device.TISensorTag;
import iot.agile.object.AbstractAgileObject;
import iot.agile.object.DeviceDefinition;
import iot.agile.object.DeviceOverview;

/**
 * @author dagi
 *
 *         Agile Device manager implementation
 *
 */
public class DeviceManagerImp extends AbstractAgileObject implements DeviceManager {

	protected final Logger logger = LoggerFactory.getLogger(DeviceManagerImp.class);

	/**
	 * Bus name for the device manager
	 */
	private static final String AGILE_DEVICEMANAGER_MANAGER_BUS_NAME = "iot.agile.DeviceManager";
	/**
	 * Bus path for the device manager
	 */
	private static final String AGILE_DEVICEMANAGER_MANAGER_BUS_PATH = "/iot/agile/DeviceManager";

	/**
	 * registered devices
	 */
	protected final List<DeviceDefinition> devices = new ArrayList<DeviceDefinition>();

	public static void main(String[] args) throws DBusException {
		DeviceManager deviceManager = new DeviceManagerImp();
	}

	public DeviceManagerImp() throws DBusException {

		dbusConnect(AGILE_DEVICEMANAGER_MANAGER_BUS_NAME, AGILE_DEVICEMANAGER_MANAGER_BUS_PATH, this);
		logger.debug("Started Device Manager");
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Find()
	 */
	@Override
	public String Find() {
		// TODO
		return null;
	}

	@Override
	public List<String> MatchingDeviceTypes(DeviceOverview deviceOverview) {
		List<String> ret = new ArrayList();
		if(TISensorTag.Matches(deviceOverview)) {
			ret.add(TISensorTag.deviceTypeName);
		}
		if(MedicalDevice.Matches(deviceOverview)) {
			ret.add(MedicalDevice.deviceTypeName);
		}
		return ret;
	}


	@Override
	public DeviceDefinition Register(DeviceOverview deviceOverview, String deviceType) {
		Device device = null;
		DeviceDefinition registeredDev = null;
		try {
			if (deviceType.equals(TISensorTag.deviceTypeName)) {
				logger.info("Creating new {}", TISensorTag.deviceTypeName);
				device = new TISensorTag(deviceOverview);
			} else if (deviceType.equals(MedicalDevice.deviceTypeName)) {
				logger.info("Creating new {}", MedicalDevice.deviceTypeName);
				device = new MedicalDevice(deviceOverview);
			}
			if (device != null) {
				registeredDev = device.Definition();
				devices.add(registeredDev);
			}
		} catch (Exception e) {
			logger.error("Can not register device: {}", e.getMessage());
			e.printStackTrace();
		}

		// connect device
		if (device != null) {
			final Device dev = device;
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						dev.Connect();
						logger.info("Device connected");
					} catch (Exception e) {
						logger.error("Error encountered while attempting to connect: {}", e.getMessage());
					}
				}
			}).start();
		}
		return registeredDev;
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Create()
	 */
	@Override
	public DeviceDefinition Create(DeviceDefinition deviceDefinition) {
		logger.debug("Creating new device id: {} name: {} protocol: {}", deviceDefinition.address,
				deviceDefinition.name, deviceDefinition.protocol);
		boolean registered = false;
		DeviceDefinition registeredDev = null;

		// For demo purpose we create only sensor tag device
		Device device = getDevice(deviceDefinition);

		// Register device
		try {
			if (device == null) {
				registeredDev = new DeviceDefinition("ble" + deviceDefinition.address.replace(":", ""),
						deviceDefinition.address, deviceDefinition.name, deviceDefinition.description,
						deviceDefinition.protocol, "/iot/agile/Device/ble" + deviceDefinition.address.replace(":", ""),
						deviceDefinition.streams);
				devices.add(registeredDev);
				if(deviceDefinition.name.contains("SensorTag")){
					device = new TISensorTag(registeredDev);
				}else if(deviceDefinition.name.contains("Medical")){
					logger.info("Medical device registered");
					device = new MedicalDevice(registeredDev);
 				} 
				logger.info("Device registered: {}", device.Id());
			} else {
				registeredDev = deviceDefinition;
				logger.info("Device already registered:  {}", device.Id());
			}
			registered = true;
		} catch (Exception e) {
			logger.error("Can not register device: {}", e.getMessage());
		}

		// connect device
		if (registered) {
			final Device dev = device;
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						dev.Connect();
						logger.info("Device connected: {}", deviceDefinition.address);
					} catch (Exception e) {
						logger.error("Error encountered while attempting to connect: {}", e.getMessage());
					}
				}
			}).start();

		}
		return registeredDev;
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Read(java.lang.
	 *      String)
	 */
	@Override
	public DeviceDefinition Read(String id) {
		for (DeviceDefinition dd : devices) {
			if (dd.deviceId.trim().equals(id)) {
				return dd;
			}
		}
		return null;
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Update(java.lang.
	 *      String, java.lang.String)
	 */
	@Override
	public void Update(String id, DeviceDefinition definition) {
		logger.debug("DeviceManager.Update not implemented");
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Devices()
	 */
	@Override
	public List<DeviceDefinition> Devices() {
		return devices;
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Delete(java.lang.
	 *      String, java.lang.String)
	 */
	@Override
	public void Delete(String id) {
		DeviceDefinition devDefn = Read(id);
		if (devDefn != null) {
			Device device = getDevice(devDefn);
			if (device != null) {
				try {
					device.Disconnect();
					connection.unExportObject(devDefn.path);
					devices.remove(devDefn);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.devicemanager.DeviceManager#Batch(java.lang.
	 *      String, java.lang.String)
	 */
	@Override
	public void Batch(String operation, String arguments) {
		logger.debug("DeviceManager.Batch not implemented");
	}

	/**
	 * (non-Javadoc)
	 *
	 * @see org.freedesktop.dbus.DBusInterface#isRemote()
	 */
	@Override
	public boolean isRemote() {
		return false;
	}

	/**
	 * Get device based on {@code DeviceDefinition}
	 * 
	 * @param devDef
	 *            Device definition
	 * @return
	 */
	private Device getDevice(DeviceDefinition devDef) {
		String objectName = "iot.agile.Device";
		String objectPath = "/iot/agile/Device/ble" + devDef.address.replace(":", "");
		try {
			DBusConnection connection = DBusConnection.getConnection(DBusConnection.SESSION);
			Device device = (Device) connection.getRemoteObject(objectName, objectPath);
			return device;
		} catch (Exception e) {
			return null;
		}

	}
}
