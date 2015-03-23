package net.geant.coco.agent.portal.utils;

import java.util.List;

import net.geant.coco.agent.portal.dao.NetworkSite;
import net.geant.coco.agent.portal.dao.NetworkSwitch;

public class Pce {
    private List<NetworkSwitch> networkSwitches;
    private List<NetworkSite> networkSites;
    private Topology topology = new Topology();
    private static int flowId = 1;

    private int getNextFlowId() {
        flowId++;
        return flowId;
    }

    public Pce(List<NetworkSwitch> networkSwitches,
            List<NetworkSite> networkSites) {
        this.networkSwitches = networkSwitches;
        this.networkSites = networkSites;
    }

    public void setupCoreForwarding() {
        /*
         * Loop through all pairs of sites. Using 'to' in outer loop causes
         * inner loop to have all paths towards a site.
         */
        for (NetworkSite toSite : networkSites) {
            for (NetworkSite fromSite : networkSites) {
                // skip if 'from' and 'to' are the same site
                if (fromSite.getName().equals(toSite.getName())) {
                    continue;
                }
                List<CoCoLink> edgesList = topology.calculatePath(
                        fromSite.getName(), toSite.getName());
                if (edgesList == null) {
                    System.out.println("no path");
                    continue;
                }
                CoCoLink[] edges = edgesList.toArray(new CoCoLink[0]);
                for (int i = 1; i < edges.length; i++) {
                    String switchName = edges[i - 1].getDstNode();
                    // check if this is a core P node
                    if (topology.getNode(switchName).getType() == NodeType.P) {
                        System.out.println("P node config on " + switchName
                                + ": " + edges[i - 1].getDstTp() + " -> "
                                + edges[i].getSrcTp());
                        Flow entry = new Flow(switchName, getNextFlowId());
                        entry.inPort(edges[i - 1].getDstTpNr());
                        entry.matchMplsLabel(topology.getNode(
                                toSite.getProviderSwitch()).getPeMplsLabel());
                        entry.outPort(edges[i].getSrcTpNr());
                        System.out.println(entry.buildFlow());
                    }
                }
            }
        }
    }

    public void addSiteToVpn(NetworkSite toSite, int vpnMplsLabel, List<NetworkSite> vpnSites) {
        Flow flowEntry;
        int flowNr;

        // loop through all the sites within the VPN
        for (NetworkSite fromSite : vpnSites) {
            if (fromSite.getName().equals(toSite.getName())) {
                continue;
            }

            System.out.println("addSiteToVpn: " + toSite.getName());

            // check if both sites are on the same switch
            if (fromSite.getProviderSwitch().equals(toSite.getProviderSwitch())) {
                // build incoming flow to 'toSite' on its PE switch
                flowEntry = new Flow(toSite.getProviderSwitch(),
                        getNextFlowId());

                flowEntry.inPort(String.valueOf(fromSite.getProviderPort()));
                flowEntry.matchVlan(topology.getNode(fromSite.getName())
                        .getVlan());
                flowEntry.matchDstIpv4Prefix(topology.getNode(toSite.getName())
                        .getIpv4Prefix());
                flowEntry
                        .setDstMAC(topology.getNode(toSite.getName()).getMac());
                flowEntry.modVlan(topology.getNode(toSite.getName()).getVlan());
                flowEntry.outPort(String.valueOf(toSite.getProviderPort()));
                System.out.println(flowEntry.buildFlow());

                // build outgoing flow from 'toSite' on its PE switch
                flowEntry = new Flow(toSite.getProviderSwitch(),
                        getNextFlowId());
                flowEntry.inPort(String.valueOf(toSite.getProviderPort()));
                flowEntry.matchVlan(topology.getNode(toSite.getName())
                        .getVlan());
                flowEntry.matchDstIpv4Prefix(topology.getNode(
                        fromSite.getName()).getIpv4Prefix());
                flowEntry.setDstMAC(topology.getNode(fromSite.getName())
                        .getMac());
                flowEntry.modVlan(topology.getNode(fromSite.getName())
                        .getVlan());
                flowEntry.outPort(String.valueOf(fromSite.getProviderPort()));
                System.out.println(flowEntry.buildFlow());
            } else {
                List<CoCoLink> edgesList = topology.calculatePath(
                        fromSite.getName(), toSite.getName());
                CoCoLink[] edges = edgesList.toArray(new CoCoLink[0]);

                System.out.println("set fwd entry on " + toSite.getProviderSwitch() + " PE switch of "
                        + fromSite.getName() + " --> " + toSite.getName());
                flowNr = getNextFlowId();
                flowEntry = new Flow(fromSite.getProviderSwitch(),
                        flowNr);
                flowEntry.inPort(edges[0].getDstTpNr());
                flowEntry.matchVlan(String.valueOf(fromSite.getVlanId()));
                flowEntry.matchDstIpv4Prefix(toSite.getIpv4Prefix());
                flowEntry.pushVpnMplsLabel(String.valueOf(vpnMplsLabel));
                flowEntry.pushPeMplsLabel(topology.getNode(
                        toSite.getProviderSwitch()).getPeMplsLabel());
                flowEntry.outPort(edges[1].getSrcTpNr());
                RestClient.sendtoSwitch(fromSite.getProviderSwitch(), flowEntry.buildFlow(), String.valueOf(flowNr));

                System.out.println("set fwd entry on " + toSite.getProviderSwitch() + " PE switch of "
                        + fromSite.getName() + " <-- " + toSite.getName());
                flowNr = getNextFlowId();
                flowEntry = new Flow(fromSite.getProviderSwitch(),
                        flowNr);
                flowEntry.inPort(edges[1].getSrcTpNr());
                flowEntry.matchMplsLabel(topology.getNode(edges[0].getDstNode()).getPeMplsLabel());
                flowEntry.matchDstIpv4Prefix(fromSite.getIpv4Prefix());
                flowEntry.popTwoMplsLabels();
                flowEntry.modVlan(String.valueOf(fromSite.getVlanId()));
                flowEntry.setDstMAC(fromSite.getMacAddress());
                flowEntry.outPort(edges[0].getDstTpNr());
                RestClient.sendtoSwitch(fromSite.getProviderSwitch(), flowEntry.buildFlow(), String.valueOf(flowNr));
                
                System.out.println("set ingress fwd entry on PE switch of "
                        + toSite.getName() + " --> " + fromSite.getName());
                System.out.println("set egress fwd entry on PE switch of "
                        + toSite.getName() + " --> " + fromSite.getName());

            }
        }
    }
}