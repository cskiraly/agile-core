/*******************************************************************************
 * Copyright (C) 2017 Create-Net / FBK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Create-Net / FBK - initial API and implementation
 ******************************************************************************/
/*
 * Copyright 2016 CREATE-NET
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
package iot.agile.http.resource;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import iot.agile.exception.AgileDeviceNotFoundException;
import iot.agile.exception.AgileNoResultException;
import iot.agile.http.Util;
import iot.agile.http.resource.devicemanager.BatchBody;
import iot.agile.http.service.DbusClient;
import iot.agile.object.DeviceDefinition;
import iot.agile.object.DeviceOverview;

/**
 *
 * @author Luca Capra <lcapra@create-net.org>
 */
@Path("/devices")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DeviceManager {

	protected Logger logger = LoggerFactory.getLogger(DeviceManager.class);

	@Inject
	DbusClient client;

	ObjectMapper mapper = Util.mapper;

	@Context
	private HttpServletResponse response;

	@GET
	@Path("/typeof")
	public List<String> MatchingDeviceTypesDEPRECATED(DeviceOverview overview) throws DBusException, IOException {
		logger.warn("DEPRECATED GET /typeof called");
		return MatchingDeviceTypes(overview);
	}

	@POST
	@Path("/typeof")
	public List<String> MatchingDeviceTypes(DeviceOverview overview) throws DBusException, IOException {
		List<String> ret = new ArrayList<String>();
		try {
			ret = client.getDeviceManager().MatchingDeviceTypes(overview);
		} catch (org.freedesktop.dbus.exceptions.DBusExecutionException e) {
			logger.warn("GOT IT. Now what? " + e + overview);
		}
		return ret;
	}

	public static class RegisterPayload {
		@JsonProperty("overview")
		public DeviceOverview overview;
		@JsonProperty("type")
		public String type;

		@JsonCreator
		public RegisterPayload(@JsonProperty("overview") DeviceOverview overview, @JsonProperty("type") String type){
			this.overview = overview; this.type = type;
		}
	}

	@POST
	@Path("/register")
	public DeviceDefinition RegisterDEPRECATED(RegisterPayload args) throws DBusException, IOException {
		logger.warn("DEPRECATED POST /register called");
		return Register(args);
	}

	@POST
	public DeviceDefinition Register(RegisterPayload args) throws DBusException, IOException {
		logger.debug("Register new device of type {}: {} ({}) on {}", args.type, args.overview.id, args.overview.name, args.overview.protocol);
		return client.getDeviceManager().Register(args.overview, args.type);
	}

	@GET
	public List<DeviceDefinition> Devices() throws DBusException, JsonProcessingException {
		List<DeviceDefinition> devices = client.getDeviceManager().Devices();
		return (devices==null ||devices.size()==0)? null: devices;
	}

	@POST
	@Path("/find")
	public String Find() {
		throw new InternalError("Not implemented");
	}

	@GET
	@Path("/{id}")
	public DeviceDefinition Read(@PathParam("id") String id) throws DBusException {
    try {
      return client.getDeviceManager().Read(id);
    } catch (AgileNoResultException e) {
      return null;
    } catch (Exception ex) {
      throw new WebApplicationException("Error on reading device", ex);
    }
	}

	@PUT
	@Path("/{id}")
	public void Update(@PathParam("id") String id, DeviceDefinition definition) throws DBusException {
		// TODO: check consistency of id and definition.get("id);
		client.getDeviceManager().Update(id, definition);
	}

	@DELETE
	@Path("/{id}")
	public void Delete(@PathParam("id") String id) throws DBusException {
    try {
      client.getDeviceManager().Delete(id);
    } catch (AgileDeviceNotFoundException e) {
      logger.debug("Device not found ex");
      throw e;
    } catch (Exception e) {
      throw new WebApplicationException("Error on deleting device", e);
    }
  }

	@POST
	@Path("/batch")
	public void Batch(BatchBody body) throws DBusException {
		client.getDeviceManager().Batch(body.operation, body.arguments);
	}

}
