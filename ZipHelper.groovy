package es.eci.utils

import java.io.File;
import java.util.List;
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipHelper {
	
	/**
	 * Descomprime un fichero Zip a un directorio determinado
	 * @param zipFile Fichero zip a descomprimir
	 * @param folder Directorio de destino
	 * @param inclusions Lista de expresiones regulares a incluir
	 * @param exclusions Lista de expresiones regulares a excluir
	 */
	public static void unzipFile(File zipFile, File folder, List<String> inclusions = null, List<String> exclusions = null) {
		if (zipFile != null && folder != null && zipFile.exists() && zipFile.isFile()) {
			unzipInputStream(new FileInputStream(zipFile), folder, inclusions, exclusions);
		}
	}
	
	/**
	 * Descomprime un fichero Zip a un directorio determinado
	 * @param is InputStream binario del que leer la información. 
	 * @param folder Directorio de destino
	 * @param inclusions Lista de expresiones regulares a incluir
	 * @param exclusions Lista de expresiones regulares a excluir 
	 */
	public static void unzipInputStream(InputStream is, File folder, List<String> inclusions = null, List<String> exclusions = null) {
		if (is != null && folder != null ) {
			byte[] buffer = new byte[1024];		 
	    	if(!folder.exists()){
	    		folder.mkdirs();
	    	}
	 
	    	String outputFolder = folder.getCanonicalPath()
	    	
	    	ZipInputStream zis = 
	    		new ZipInputStream(is);
	    	// Entradas del zip
	    	ZipEntry ze = zis.getNextEntry();
	 
	    	while(ze!=null){
	 
	    	   String fileName = ze.getName();
	           File newFile = new File(outputFolder + File.separator + fileName);
	 
	           // Construir la ruta de descompresión
	           new File(newFile.getParent()).mkdirs();

			   if (!ze.isDirectory()) {
				   if (isEmpty(inclusions) || acceptsPattern(inclusions, newFile.getName())) {  
					   if (isEmpty(exclusions) || !acceptsPattern(exclusions, newFile.getName())) {
				           FileOutputStream fos = new FileOutputStream(newFile);             
				 
				           int len;
				           while ((len = zis.read(buffer)) != -1) {
							   fos.write(buffer, 0, len);
				           }
				 
				           fos.close();   
					   }
				   }
			   }
	           newFile.setLastModified(ze.getTime())
	           ze = zis.getNextEntry();
	    	}
	 
	        zis.closeEntry();
	    	zis.close();
		}
	}
	
	// Indica si una lista está vacía
	private static boolean isEmpty(List<String> lista) {
		return ( lista == null || lista.size() == 0 )
	}
	
	// Indica si el fichero cumple con unos determinados patrones
	private static acceptsPattern(List<String> patterns, String name) {
		boolean ret = false;
		if (patterns != null && patterns.size() > 0) {
			for (String pattern: patterns) {
				ret |= (name.toLowerCase() ==~ pattern)
			}
		}
		return ret;
	}
	
	/**
	 * Creación de un zip a partir de un directorio adaptada de javacodegeeks.com
	 * @param srcDir Directorio de origen 
	 * @param destino Fichero de destino
	 * @return Zip del directorio de origen
	 */
	public static File addDirToArchive(File srcDir, File destino = null) {		
		if (srcDir != null && srcDir.exists() && srcDir.isDirectory()) {
			try {
				if (destino == null) {
					destino = File.createTempFile("nexus", ".zip")
				} 
				else if (!destino.exists()) {
					destino.createNewFile();
				}
	            FileOutputStream fos = new FileOutputStream(destino);
	            ZipOutputStream zos = null
				try {
					// Intentar con el constructor de java 7, donde esté disponible
					zos = new ZipOutputStream(fos, java.nio.charset.Charset.forName("UTF-8"));
				}
				catch (Exception e) {
					// Java 6
					zos = new ZipOutputStream(fos);
				}
	            addDirToArchiveI(zos, srcDir, srcDir);
	            // close the ZipOutputStream
	            zos.close();
	        }
	        catch (IOException ioe) {
	            System.out.println("Error creating zip file: " + ioe);
	        }
		}
		return destino
	}
	
	/** Inmersión recursiva */
	private static void addDirToArchiveI(ZipOutputStream zos, File srcFile, File srcDir) {
        File[] files = srcFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            // if the file is directory, use recursion
            if (files[i].isDirectory()) {
                addDirToArchiveI(zos, files[i], srcDir);
                continue;
            }
            try {
                byte[] buffer = new byte[1024];
                FileInputStream fis = new FileInputStream(files[i]);
                // Respeta la ruta relativa del fichero
				String ruta = Utiles.rutaRelativa(srcDir, srcFile)
				if (ruta != null && ruta.trim().size() > 0) {
					ruta += System.getProperty("file.separator")
				}
				ZipEntry ze = new ZipEntry(ruta + files[i].getName())
				ze.setTime(files[i].lastModified())
                zos.putNextEntry(ze);
                int length;
                while ((length = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, length);
                }
                // Limpieza
                zos.closeEntry();
                fis.close();
            } catch (IOException ioe) {
                System.out.println("IOException :" + ioe);
            }
        }
    }

}