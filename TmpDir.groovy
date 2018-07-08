package es.eci.utils

class TmpDir {
	/** Closure a ejecutarse sobre un directorio temporal. */
	static void tmp(Closure c) {
		File tempDir = File.createTempFile("tmpDir", "tmp")
		tempDir.delete()
		tempDir.mkdir()
		try {
			c(tempDir)
		}
		finally {
			tempDir.deleteDir() 
		}
	}
}