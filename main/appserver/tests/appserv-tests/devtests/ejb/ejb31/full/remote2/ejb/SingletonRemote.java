package com.acme;

import javax.ejb.Remote;

@Remote
public interface SingletonRemote {

  void doTest(String targetAppName);

}

