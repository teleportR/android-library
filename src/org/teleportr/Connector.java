package org.teleportr;

import java.util.Date;



public interface Connector {
	
	public void getRides(Place orig, Place dest, Date dep, Date arr);

	public void postRide(Place orig, Place dest, Date dep, Date arr);
	

}
