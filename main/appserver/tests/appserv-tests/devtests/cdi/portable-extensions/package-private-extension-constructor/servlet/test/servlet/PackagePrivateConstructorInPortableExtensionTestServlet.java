/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package test.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import test.beans.Preferred;
import test.beans.TestBean;
import test.beans.TransactionInterceptor;
import test.extension.PackagePrivateConstructorExtension;

@WebServlet(name = "mytest", urlPatterns = { "/myurl" })
public class PackagePrivateConstructorInPortableExtensionTestServlet extends HttpServlet {
    @Inject
    @Preferred
    TestBean tb;
    
    @Inject
    BeanManager bm;

    public void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        PrintWriter writer = res.getWriter();
        writer.write("Hello from Servlet 3.0.");
        String msg = "";

        if (tb == null)
            msg += "Injection of request scoped bean failed";

        tb.m1();
        if (!TransactionInterceptor.aroundInvokeCalled)
            msg += "Business method interceptor aroundInvoke not called";
        tb.m2();
        if (TransactionInterceptor.aroundInvokeInvocationCount != 2)
            msg += "Business method interceptor invocation on method-level "
                    + "interceptor annotation count not expected. "
                    + "expected =2, actual="
                    + TransactionInterceptor.aroundInvokeInvocationCount;
        if (!TransactionInterceptor.errorMessage.trim().equals(""))
            msg += TransactionInterceptor.errorMessage;
        
        //check if our portable extension was called
        if (!PackagePrivateConstructorExtension.packagePrivateConstructorCalled)
            msg += "Portable Extension package private constructor:  not called";
        
        if (!PackagePrivateConstructorExtension.beforeBeanDiscoveryCalled)
            msg += "Portable Extension lifecycle observer method: " +
            		"beforeBeanDiscovery not called";

        if (!PackagePrivateConstructorExtension.afterBeanDiscoveryCalled)
            msg += "Portable Extension lifecycle observer method: " +
            		"afterBeanDiscovery not called or injection of BeanManager " +
            		"in an observer method failed";
        
        if (!PackagePrivateConstructorExtension.processAnnotatedTypeCalled)
            msg += "Portable Extension lifecycle observer method: process " +
            		"annotated type not called";

        if((bm.getBeans(PackagePrivateConstructorExtension.class, new AnnotationLiteral<Any>(){}).iterator().next().getClass()) == null) 
            msg += "Portable Extension not available for lookup through BeanManager";

        writer.write(msg + "\n");
    }

}
