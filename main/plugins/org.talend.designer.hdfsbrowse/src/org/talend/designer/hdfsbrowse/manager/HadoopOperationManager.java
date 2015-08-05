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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.talend.core.repository.ConnectionStatus;
import org.talend.core.utils.ReflectionUtils;
import org.talend.core.utils.TalendQuoteUtils;
import org.talend.cwm.relational.RelationalFactory;
import org.talend.cwm.relational.TdTable;
import org.talend.designer.hdfsbrowse.model.HDFSConnectionBean;
import org.talend.designer.hdfsbrowse.model.HDFSFile;
import org.talend.designer.hdfsbrowse.model.HDFSFolder;
import org.talend.designer.hdfsbrowse.model.HDFSPath;

/**
 * DOC ycbai class global comment. Detailled comment
 */
public class HadoopOperationManager {

    protected static final String PATH_SEPARATOR = "/"; //$NON-NLS-1$

    private static HadoopOperationManager instance = new HadoopOperationManager();

    private HadoopOperationManager() {
    }

    public static HadoopOperationManager getInstance() {
        return instance;
    }

    public void loadHDFSFolderChildren(HDFSConnectionBean connection, Object fileSystem, ClassLoader classLoader,
            HDFSPath parent, String path) throws IOException, InterruptedException, URISyntaxException, InstantiationException,
            IllegalAccessException, ClassNotFoundException, SecurityException, IllegalArgumentException, NoSuchMethodException,
            InvocationTargetException {
        if (connection == null || fileSystem == null || classLoader == null || parent == null || path == null) {
            return;
        }
        Object pathObj = ReflectionUtils.newInstance("org.apache.hadoop.fs.Path", classLoader, new Object[] { path });
        Object[] statusList = (Object[]) ReflectionUtils.invokeMethod(fileSystem, "listStatus", new Object[] { pathObj });
        if (statusList == null) {
            return;
        }
        for (Object status : statusList) {
            if (!canAccess(connection, status)) {
                continue;
            }
            HDFSPath content = null;
            Object statusPath = ReflectionUtils.invokeMethod(status, "getPath", new Object[0]);
            String pathName = (String) ReflectionUtils.invokeMethod(statusPath, "getName", new Object[0]);
            // String absolutePath = ((URI) ReflectionUtils.invokeMethod(statusPath, "toUri", new
            // Object[0])).toString();
            // Get path from toString method since convert to URI will escape some special characters. Need test...
            String absolutePath = (String) ReflectionUtils.invokeMethod(statusPath, "toString", new Object[0]);
            String relativePath = getRelativePath(connection, absolutePath);
            if ((Boolean) ReflectionUtils.invokeMethod(status, "isDir", new Object[0])) {
                content = new HDFSFolder(parent);
            } else {
                content = new HDFSFile(parent);
                content.setTable(createTable(trimFileExtention(pathName)));
            }
            content.setPath(relativePath);
            content.setValue(pathName);
            parent.addChild(content);
        }
    }

    public long getFileSize(Object fileSystem, ClassLoader classLoader, String filePath) throws InterruptedException,
            URISyntaxException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException,
            SecurityException, IllegalArgumentException, NoSuchMethodException, InvocationTargetException {
        long size = 0;
        Object pathObj = ReflectionUtils.newInstance("org.apache.hadoop.fs.Path", classLoader, new Object[] { filePath });
        Object fileStatus = ReflectionUtils.invokeMethod(fileSystem, "getFileStatus", new Object[] { pathObj });
        size = (Long) ReflectionUtils.invokeMethod(fileStatus, "getLen", new Object[0]);

        return size;
    }

    public InputStream getFileContent(Object fileSystem, ClassLoader classLoader, String filePath) throws IOException,
            InterruptedException, URISyntaxException, InstantiationException, IllegalAccessException, ClassNotFoundException,
            SecurityException, IllegalArgumentException, NoSuchMethodException, InvocationTargetException {
        if (fileSystem == null) {
            return null;
        }
        Object pathObj = ReflectionUtils.newInstance("org.apache.hadoop.fs.Path", classLoader, new Object[] { filePath });
        return (InputStream) ReflectionUtils.invokeMethod(fileSystem, "open", new Object[] { pathObj });
    }

    public InputStream getFileContent(HDFSConnectionBean connection, String filePath) throws IOException, InterruptedException,
            URISyntaxException, InstantiationException, IllegalAccessException, ClassNotFoundException, SecurityException,
            IllegalArgumentException, NoSuchMethodException, InvocationTargetException, ExecutionException {
        Object fileSystem = getDFS(connection);
        if (fileSystem == null) {
            return null;
        }
        ClassLoader classLoader = getClassLoader(connection);
        return getFileContent(fileSystem, classLoader, filePath);
    }

    public ConnectionStatus testConnection(HDFSConnectionBean connection) {
        return HadoopServerUtil.testConnection(connection);
    }

    public Object getDFS(HDFSConnectionBean connectionBean) throws IOException, InterruptedException, URISyntaxException,
            InstantiationException, IllegalAccessException, ClassNotFoundException, SecurityException, IllegalArgumentException,
            NoSuchMethodException, InvocationTargetException, ExecutionException {
        return HadoopServerUtil.getDFS(connectionBean);
    }

    public ClassLoader getClassLoader(HDFSConnectionBean connectionBean) {
        return HadoopServerUtil.getClassLoader(connectionBean);
    }

    private String trimFileExtention(String fileName) {
        if (fileName.indexOf(".") != -1) { //$NON-NLS-1$
            fileName = fileName.substring(0, fileName.lastIndexOf(".")); //$NON-NLS-1$
        }
        return fileName;
    }

    private String getRelativePath(HDFSConnectionBean connection, String absPath) {
        String nameNodeURI = TalendQuoteUtils.removeQuotesIfExist(connection.getNameNodeURI());
        if (absPath.startsWith(nameNodeURI)) {
            absPath = absPath.substring(absPath.indexOf(nameNodeURI) + nameNodeURI.length());
        }
        if (!absPath.startsWith(PATH_SEPARATOR)) {
            absPath = PATH_SEPARATOR + absPath;
        }

        return absPath;
    }

    private TdTable createTable(String tableName) {
        TdTable table = RelationalFactory.eINSTANCE.createTdTable();
        table.setName(tableName);
        table.setLabel(tableName);

        return table;
    }

    private boolean canAccess(HDFSConnectionBean connection, Object status) throws ClassNotFoundException, SecurityException,
            IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (status == null) {
            return false;
        }
        String userName = null;
        if (connection != null) {
            userName = connection.getUserName();
        }
        return HadoopServerUtil.hasReadAuthority(status, userName);
    }

}
