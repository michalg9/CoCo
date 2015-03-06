package net.geant.coco.agent.portal.beans;

public enum InterfaceType {
	UNI("UNI"),
	NNI("NNI"),
	ENNI("ENNI");
	
	private String name; 
    private InterfaceType(String name) { 
        this.name = name; 
    } 
    
    @Override 
    public String toString(){ 
        return name; 
    } 
}
