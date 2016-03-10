package net.geant.coco.agent.portal.controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.extern.slf4j.Slf4j;
import net.geant.coco.agent.portal.dao.NetworkElement;
import net.geant.coco.agent.portal.dao.NetworkInterface;
import net.geant.coco.agent.portal.dao.NetworkSite;
import net.geant.coco.agent.portal.dao.Vpn;
import net.geant.coco.agent.portal.rest.RestVpnURIConstants;
import net.geant.coco.agent.portal.service.NetworkSitesService;
import net.geant.coco.agent.portal.service.TopologyService;
import net.geant.coco.agent.portal.service.VpnsService;
import net.geant.coco.agent.portal.utils.VpnProvisioner;

@Slf4j
@Controller
@Configuration
@PropertySource("classpath:/net/geant/coco/agent/portal/props/config.properties")
public class PortalControllerIntent {

	@Autowired
	Environment env;
	
	private NetworkSitesService networkSitesService;
	private VpnsService vpnsService;
	private TopologyService topologyService;
	
	Map<String, NetworkSite> networkSites;
	
	boolean networkChanged = false;
	
	VpnProvisioner vpnProvisioner;
	
	//RestUtils restUtils = new RestUtils();
	
	@Autowired
	public void setNetworkSitesService(NetworkSitesService networkSitesService) {
		this.networkSitesService = networkSitesService;
	}

	@Autowired
	public void setVpnsService(VpnsService vpnsService) {
		this.vpnsService = vpnsService;
	}

	@Autowired
	public void setTopologyService(TopologyService topologyService) {
		this.topologyService = topologyService;
	}
	
	private void networkAddSiteToVpn(String vpnName, String addSiteName) {
		log.info("networkAddSiteToVpn vpnName=" + vpnName + "siteName=" + addSiteName);
		
		vpnsService.addSiteToVpn(vpnName, addSiteName);
//		NetworkSite networkSite = networkSites.get(addSiteName);
//		int status = vpnProvisioner.addSite(vpnName, addSiteName, networkSite.getIpv4Prefix(),
//				networkSite.getProviderSwitch() + ":" + networkSite.getProviderPort());
//		log.info("addSite returns: " + status);

	}

	private void networkDeleteSiteFromVpn(String vpnName, String deleteSiteName) {
		log.info("networkAddSiteToVpn vpnName=" + vpnName + "siteName=" + deleteSiteName);
		
		vpnsService.deleteSiteFromVpn(vpnName, deleteSiteName);
//		NetworkSite networkSite = networkSites.get(deleteSiteName);
//		int status = vpnProvisioner.deleteSite(vpnName, deleteSiteName, networkSite.getIpv4Prefix(),
//				networkSite.getProviderSwitch() + ":" + networkSite.getProviderPort());
//		log.info("addSite returns: " + status);
	}
	
	
	@RequestMapping(value = RestVpnURIConstants.GET_TOPOLOGY_VIS, method = RequestMethod.GET)
	public @ResponseBody String getTopologyVis() {
		StringBuilder visJson = new StringBuilder();

		Set<NetworkElement> nodeSet = new HashSet<NetworkElement>();

		List<NetworkInterface> networkInterfaces = topologyService.getNetworkInterfaces();

		for (NetworkInterface networkInterface : networkInterfaces) {
			if (!nodeSet.contains(networkInterface.source)) {
				nodeSet.add(networkInterface.source);
			}

			if (!nodeSet.contains(networkInterface.neighbour)) {
				nodeSet.add(networkInterface.neighbour);
			}
		}

		visJson.append("{\"nodes\" : [ ");
		for (NetworkElement networkElement : nodeSet) {
			int fakeId = 0;
			if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.CUSTOMER)) {
				fakeId = networkElement.getId();
			} else if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.EXTERNAL_AS)) {
				fakeId = 100 + networkElement.getId();

				// TODO continue to ignore external as sites
				continue;

			} else if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.SWITCH)) {
				fakeId = 200 + networkElement.getId();
			}
			visJson.append("{\"id\": \"");
			visJson.append(fakeId);
			visJson.append("\", \"label\": \"");
			visJson.append(networkElement.getName());
			visJson.append("\", \"group\": \"");
			visJson.append(networkElement.nodeType);
			visJson.append("\"}, ");
		}

		visJson.deleteCharAt(visJson.lastIndexOf(","));
		visJson.append("],");
		visJson.append("\"edges\" : [");

		for (NetworkInterface networkInterface : networkInterfaces) {
			int fakeId = 0;
			NetworkElement networkElement;

			networkElement = networkInterface.source;
			fakeId = 0;
			if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.CUSTOMER)) {
				fakeId = networkElement.getId();
			} else if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.EXTERNAL_AS)) {
				fakeId = 100 + networkElement.getId();
			} else if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.SWITCH)) {
				fakeId = 200 + networkElement.getId();
			}
			visJson.append("{\"from\": \"");
			visJson.append(fakeId);

			networkElement = networkInterface.neighbour;
			fakeId = 0;
			if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.CUSTOMER)) {
				fakeId = networkElement.getId();
			} else if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.EXTERNAL_AS)) {
				fakeId = 100 + networkElement.getId();
			} else if (networkElement.nodeType.equals(NetworkElement.NODE_TYPE.SWITCH)) {
				fakeId = 200 + networkElement.getId();
			}
			visJson.append("\", \"to\": \"");
			visJson.append(fakeId);
			visJson.append("\"}, ");
		}
		visJson.deleteCharAt(visJson.lastIndexOf(","));
		visJson.append("]}");

		return visJson.toString();
	}

	@RequestMapping(value = RestVpnURIConstants.GET_TOPOLOGY_IS_CHANGED, method = RequestMethod.GET)
	public @ResponseBody boolean getTopologyChange() {

		if (networkChanged == true) {
			networkChanged = false;
			return true;
		}

		return networkChanged;
	}
	
	@RequestMapping(value = RestVpnURIConstants.GET_VPN, method = RequestMethod.GET)
	public @ResponseBody Vpn getVpn(@PathVariable("id") int vpnId) {
		log.info("Start getVpn. ID=" + vpnId);
		return vpnsService.getVpn(vpnId);
	}

	@RequestMapping(value = RestVpnURIConstants.UPDATE_VPN, method = RequestMethod.POST)
	public @ResponseBody Vpn updateVpn(@PathVariable("id") int vpnId, @RequestBody Vpn vpn) {
		log.info("Start updateVpn. ID=" + vpnId);

		Assert.isTrue(vpn.getId() == vpnId, "VPN id and ID from the rest path are not the same. REST PATH:"
				+ String.valueOf(vpnId) + " VPN ID" + String.valueOf(vpn.getId()));

		Vpn vpnNew = vpn;
		Vpn vpnCurrent = vpnsService.getVpn(vpnId);

		List<NetworkSite> sitesToAdd = new ArrayList<NetworkSite>(vpnNew.getSites());
		sitesToAdd.removeAll(vpnCurrent.getSites());
		List<NetworkSite> sitesToRemove = new ArrayList<NetworkSite>(vpnCurrent.getSites());
		sitesToRemove.removeAll(vpnNew.getSites());


		for (NetworkSite site : sitesToAdd) {
			networkAddSiteToVpn(vpnCurrent.getName(), site.getName());
		}
		
		for (NetworkSite site : sitesToRemove) {
			networkDeleteSiteFromVpn(vpnCurrent.getName(), site.getName());
		}

		return vpnNew;
	}
	
	
	
	@PostConstruct
	@RequestMapping("/setupAll")
	public @ResponseBody String initializeEverything() {
		log.info("Initialize everything Intent version");

		this.networkSites = networkSitesService.getNetworkSites();
		
		String controllerUrl = env.getProperty("controller.url");
		vpnProvisioner = new VpnProvisioner(controllerUrl);

		this.networkChanged = true;
		
		return "everything initialized succesfully";

	}
	
	@RequestMapping(value = RestVpnURIConstants.GET_ALL_VPN, method = RequestMethod.GET)
	public @ResponseBody List<Vpn> getAllVpns() {
		log.info("Start getAllVpns.");
		
		return vpnsService.getVpns();
	}

	@RequestMapping(value = RestVpnURIConstants.GET_ALL_SITES, method = RequestMethod.GET)
	public @ResponseBody List<NetworkSite> getSitesInVpn(@PathVariable("id") int vpnID) {
		log.info("Start getall sites.");

		return vpnsService.getSitesInVpn(vpnID);
	}

	@RequestMapping(value = RestVpnURIConstants.CREATE_VPN, method = RequestMethod.POST)
	public @ResponseBody boolean createVpn(@RequestBody Vpn vpn) {
		log.info("Start createVpn.");

		return vpnsService.createVpn(vpn);
	}

	@RequestMapping(value = RestVpnURIConstants.DELETE_VPN, method = RequestMethod.POST)
	public @ResponseBody boolean deleteVpn(@PathVariable("id") int vpnId) {
		log.info("Start deleteVpn.");
		
		return vpnsService.deleteVpn(vpnId);
	}
	
}
