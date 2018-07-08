package es.eci.utils

import java.util.concurrent.CountDownLatch

/**
 * Esta clase pretende evitar un problema de bloqueo de la clase Process al
 * intentar procesar por su cuenta en el mismo hilo la salida estándar y de error.
 */
class StreamGobbler extends Thread
{
    InputStream is;
    boolean store
	StringBuffer sb;
	CountDownLatch latch = null;
    
	StreamGobbler(InputStream is, boolean store = false, CountDownLatch latch)
    {
        this(is, store);
		this.latch = latch;
    }
	
    StreamGobbler(InputStream is, boolean store = false)
    {
        this.is = is;
        this.store = store;
        if (store) {
			sb = new StringBuffer();
        }
    }
    
	/**
	 * Imprime la salida sin embellecimientos ni saltos de línea
	 * @return Salida cruda
	 */
	public String getOut() {
		return getOut(false)
    	
    }
	
	/**
	 * Imprime la salida especificando si se quiere embellecer con saltos
	 * de línea
	 * @param pretty Si es cierto, se embellece la salida (obsoleto, se mantiene
	 * por compatibilidad)
	 * @return Salida del stream
	 */
	@Deprecated
	public String getOut(boolean pretty) {
		String ret = null;
		if(sb != null) {
			ret = sb.toString();
		}
	}
	
    public void run()
    {
        try
        {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
			boolean continueReading = true;
			int BUFFER_SIZE = 5000;
			char[] buffer = new char[BUFFER_SIZE];
			while (continueReading) {
				int read = br.read(buffer);
				if (read == -1) {
					continueReading = false;
				}
				else {
	                if (store) {
						sb.append(buffer, 0, read);
	                }    
				}
			}
        } 
        catch (IOException ioe) {
            ioe.printStackTrace();  
        }
		finally {
			if (latch != null) {
				latch.countDown();
			}
		}
    }
}