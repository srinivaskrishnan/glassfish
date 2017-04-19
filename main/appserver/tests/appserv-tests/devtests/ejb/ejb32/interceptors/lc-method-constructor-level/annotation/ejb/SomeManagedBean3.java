package com.acme;

import javax.annotation.*;

import javax.ejb.EJB;
import javax.annotation.Resource;
import javax.interceptor.ExcludeClassInterceptors;
import javax.interceptor.Interceptors;
import org.omg.CORBA.ORB;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;

@Interceptors(InterceptorC.class)
@ManagedBean("somemanagedbean3")
public class SomeManagedBean3 extends BaseBean {

    @ExcludeClassInterceptors
    @Interceptors(InterceptorA.class)
    public SomeManagedBean3() {}

    @Interceptors(InterceptorA.class)
    @PostConstruct
    private void init3() {
	System.out.println("In SomeManagedBean3::init3() " + this);
        verifyMethod("init3");
    }
    

    @Interceptors(InterceptorA.class)
    public void foo3() {
	System.out.println("In SomeManagedBean3::foo3() ");
	verifyA_AC("SomeManagedBean3");
	verifyAC_PC("SomeManagedBean3");
    }

    @PreDestroy
    private void destroy() {
	System.out.println("In SomeManagedBean3::destroy() ");
        verifyMethod("destroy");
    }
}
