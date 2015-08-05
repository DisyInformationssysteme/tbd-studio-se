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

import org.talend.core.classloader.ClassLoaderFactory;
import org.talend.designer.hdfsbrowse.model.HDFSConnectionBean;

/**
 * DOC ycbai class global comment. Detailled comment
 */
public class HadoopClassLoaderFactory {

    public static ClassLoader getClassLoader(HDFSConnectionBean connectionBean) {
        ClassLoader loader = null;
        if (connectionBean == null) {
            return loader;
        }

        String distribution = connectionBean.getDistribution();
        String version = connectionBean.getDfVersion();
        if (distribution == null || version == null) {
            return loader;
        }

        String index = "HDFS" + ":" + distribution + ":" + version; //$NON-NLS-1$  //$NON-NLS-2$  //$NON-NLS-3$
        if (connectionBean.isEnableKerberos()) {
            index += "?USE_KRB"; //$NON-NLS-1$
        }
        loader = ClassLoaderFactory.getClassLoader(index);
        if (loader == null) {
            loader = HadoopClassLoaderFactory.class.getClassLoader();
        }

        return loader;
    }

}
