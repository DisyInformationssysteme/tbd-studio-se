package org.talend.bigdata.core.di.components.hbase;

import org.apache.hadoop.hbase.client.Connection;
import org.immutables.value.Value;

import java.io.IOException;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.client.Admin;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)
public abstract class THbaseNamespace {
     abstract Connection connection();
     abstract String namespaceAction();
     abstract String namespaceName();

     public void doNamespaceAction() throws IOException {
          if (!namespaceName().equals("")){
               Admin admin = connection().getAdmin();
               boolean nsExist = false;
               try {
                    NamespaceDescriptor nd = admin.getNamespaceDescriptor(namespaceName());
                    nsExist=true;
               } catch (org.apache.hadoop.hbase.NamespaceNotFoundException e) {
                    nsExist=false;
               }

               if(!nsExist || namespaceAction().equals("CREATE")) {
                    NamespaceDescriptor nsDes = NamespaceDescriptor.create(namespaceName()).build();
                    admin.createNamespace(nsDes);
               }
          }
     }
}
