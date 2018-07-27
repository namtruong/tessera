
package com.quorum.tessera.data.migration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;


public class StoreLoaderTest {

    @Test
    public void directoryStoreLoader() {
      StoreLoader storeLoader =   StoreLoader.create(StoreType.DIR);
      assertThat(storeLoader).isExactlyInstanceOf(DirectoryStoreFile.class);
    }
    
    @Test
    public void bdbtoreLoader() {
      StoreLoader storeLoader =   StoreLoader.create(StoreType.BDB);
      assertThat(storeLoader).isExactlyInstanceOf(BdbDumpFile.class);
    }
    
    @Test(expected = java.util.NoSuchElementException.class)
    public void nullThrowsNoElementException() {
      StoreLoader.create(null);
    }
    
}
