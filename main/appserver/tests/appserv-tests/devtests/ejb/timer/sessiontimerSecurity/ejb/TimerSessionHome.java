package com.sun.s1asdev.ejb.timer.sessiontimersecurity;

import java.rmi.RemoteException;
import javax.ejb.EJBHome;
import javax.ejb.CreateException;

public interface TimerSessionHome extends EJBHome {
	TimerSession create() throws CreateException, RemoteException;
}
