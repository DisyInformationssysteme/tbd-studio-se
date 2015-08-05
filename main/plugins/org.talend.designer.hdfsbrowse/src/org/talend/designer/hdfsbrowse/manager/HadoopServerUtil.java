// ============================================================================
//
// Copyright (C) 2006-2015 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.hdfsbrowse.manager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;
import org.talend.commons.ui.runtime.exception.ExceptionHandler;
import org.talend.core.repository.ConnectionStatus;
import org.talend.core.utils.ReflectionUtils;
import org.talend.core.utils.TalendQuoteUtils;
import org.talend.designer.hdfsbrowse.model.ELinuxAuthority;
import org.talend.designer.hdfsbrowse.model.HDFSConnectionBean;

/**
 * DOC ycbai class global comment. Detailled comment
 */
public class HadoopServerUtil {

    public static final String EMPTY_STRING = ""; //$NON-NLS-1$

    public static final int timeout = 20; // the max time(second) which achieve DFS connection use.

    /**
     * DOC ycbai Comment method "getDFS".
     * 
     * Provides access to the HDFS System.
     * 
     * @param connection
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws URISyntaxException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws SecurityException
     * @throws ExecutionException
     */
    public static Object getDFS(HDFSConnectionBean connection) throws ExecutionException, InstantiationException,
            IllegalAccessException, ClassNotFoundException, SecurityException, IllegalArgumentException, NoSuchMethodException,
            InvocationTargetException, URISyntaxException, InterruptedException {
        assert connection != null;
        String nameNodeURI = connection.getNameNodeURI();
        assert nameNodeURI != null;
        nameNodeURI = TalendQuoteUtils.removeQuotesIfExist(nameNodeURI);
        String userName = StringUtils.trimToNull(connection.getUserName());
        if (userName != null) {
            userName = TalendQuoteUtils.removeQuotesIfExist(userName);
        }
        String principal = StringUtils.trimToNull(connection.getPrincipal());
        String group = StringUtils.trimToNull(connection.getGroup());
        boolean enableKerberos = connection.isEnableKerberos();

        Object dfs = null;
        ClassLoader oldClassLoaderLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader classLoader = getClassLoader(connection);
            Thread.currentThread().setContextClassLoader(classLoader);
            Object conf = Class.forName("org.apache.hadoop.conf.Configuration", true, classLoader).newInstance();
            EHadoopConfProperties.FS_DEFAULT_URI.set(conf, nameNodeURI);
            if (enableKerberos) {
                assert principal != null;
                userName = null;
                // EHadoopConfProperties.JOB_UGI.set(conf, EMPTY_STRING);
                EHadoopConfProperties.KERBEROS_PRINCIPAL.set(conf, principal);
            }
            if (group != null) {
                assert userName != null;
                // EHadoopConfProperties.KERBEROS_PRINCIPAL.set(conf, EMPTY_STRING);
                EHadoopConfProperties.JOB_UGI.set(conf, userName + "," + group); //$NON-NLS-1$
            }

            Callable<Object> dfsCallable = null;
            if (userName == null) {
                dfsCallable = getDFS(conf, classLoader);
            } else {
                dfsCallable = getDFS(new URI(nameNodeURI), conf, userName, classLoader);
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Object> future = executor.submit(dfsCallable);
            try {
                dfs = future.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoaderLoader);
        }

        return dfs;
    }

    private static Callable<Object> getDFS(final Object conf, final ClassLoader classLoader) {
        return new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                return ReflectionUtils.invokeStaticMethod("org.apache.hadoop.fs.FileSystem", classLoader, "get",
                        new Object[] { conf });
            }
        };

    }

    private static Callable<Object> getDFS(final URI uri, final Object conf, final String userName, final ClassLoader classLoader) {
        return new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                return ReflectionUtils.invokeStaticMethod("org.apache.hadoop.fs.FileSystem", classLoader, "get", new Object[] {
                        uri, conf, userName });
            }
        };

    }

    public static boolean hasReadAuthority(Object status, String userName) throws ClassNotFoundException, SecurityException,
            IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return hasAuthority(status, userName, ELinuxAuthority.READ);
    }

    public static boolean hasWriteAuthority(Object status, String userName) throws ClassNotFoundException, SecurityException,
            IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return hasAuthority(status, userName, ELinuxAuthority.WRITE);
    }

    public static boolean hasExcuteAuthority(Object status, String userName) throws ClassNotFoundException, SecurityException,
            IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return hasAuthority(status, userName, ELinuxAuthority.EXCUTE);
    }

    public static boolean hasAuthority(Object status, String userName, ELinuxAuthority authority) throws ClassNotFoundException,
            SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        boolean hasAuthority = false;
        if (status == null) {
            return hasAuthority;
        }
        if (authority == null) {
            authority = ELinuxAuthority.READ;
        }
        Object permission = ReflectionUtils.invokeMethod(status, "getPermission", new Object[0]);
        if (permission == null) {
            return hasAuthority;
        }
        userName = TalendQuoteUtils.addQuotesIfNotExist(userName);
        String owner = (String) ReflectionUtils.invokeMethod(status, "getOwner", new Object[0]);
        owner = TalendQuoteUtils.addQuotesIfNotExist(owner);
        Object userAction = ReflectionUtils.invokeMethod(permission, "getUserAction", new Object[0]);
        Object groupAction = ReflectionUtils.invokeMethod(permission, "getGroupAction", new Object[0]);
        Object otherAction = ReflectionUtils.invokeMethod(permission, "getOtherAction", new Object[0]);
        switch (authority) {
        case READ:
            if (owner != null && owner.equals(userName)) {
                return hasReadAuthority(userAction) || hasReadAuthority(groupAction);
            }
            return hasReadAuthority(otherAction);
        case WRITE:
            if (owner != null && owner.equals(userName)) {
                return hasWriteAuthority(userAction) || hasWriteAuthority(groupAction);
            }
            return hasWriteAuthority(otherAction);
        case EXCUTE:
            if (owner != null && owner.equals(userName)) {
                return hasExcuteAuthority(userAction) || hasExcuteAuthority(groupAction);
            }
            return hasExcuteAuthority(otherAction);
        default:
            break;
        }

        return hasAuthority;
    }

    private static boolean hasReadAuthority(Object action) throws ClassNotFoundException {
        if (action == null) {
            return false;
        }
        Object enumName = ((Enum) action).name();
        return "READ".equals(enumName) || "READ".equals(enumName) || "READ_WRITE".equals(enumName)
                || "READ_EXECUTE".equals(enumName) || "ALL".equals(enumName);
    }

    private static boolean hasWriteAuthority(Object action) {
        if (action == null) {
            return false;
        }
        Object enumName = ((Enum) action).name();
        return "WRITE".equals(enumName) || "WRITE_EXECUTE".equals(enumName) || "READ_WRITE".equals(enumName)
                || "ALL".equals(enumName);
    }

    private static boolean hasExcuteAuthority(Object action) {
        if (action == null) {
            return false;
        }
        Object enumName = ((Enum) action).name();
        return "EXECUTE".equals(enumName) || "READ_EXECUTE".equals(enumName) || "WRITE_EXECUTE".equals(enumName)
                || "ALL".equals(enumName);
    }

    /**
     * DOC ycbai Comment method "testConnection".
     * 
     * Test whether can connect to HDFS.
     * 
     * @return
     */
    public static ConnectionStatus testConnection(HDFSConnectionBean connection) {
        ConnectionStatus connectionStatus = new ConnectionStatus();
        connectionStatus.setResult(false);
        String errorMsg = "Cannot connect to HDFS \"" + connection.getNameNodeURI()
                + "\". Please check the connection parameters. ";
        Object dfs = null;
        try {
            dfs = getDFS(connection);
            if (dfs != null) {
                connectionStatus.setResult(true);
                connectionStatus.setMessageException("Connection successful");
            } else {
                connectionStatus.setMessageException(errorMsg);
            }
        } catch (Exception e) {
            ExceptionHandler.process(e);
            connectionStatus.setMessageException(errorMsg);
        } finally {
            if (dfs != null) {
                try {
                    ReflectionUtils.invokeMethod(dfs, "close", new Object[0]);
                } catch (Exception e) {
                }
            }
        }

        return connectionStatus;
    }

    public static ClassLoader getClassLoader(HDFSConnectionBean connection) {
        return HadoopClassLoaderFactory.getClassLoader(connection);
    }

}
