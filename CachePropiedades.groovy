package es.eci.utils

import java.util.List;
import java.util.Map;

class CachePropiedades {
	
	// Raíz de la caché
	private NodoCache raiz = null;
	// Contexto: para resolver una propiedad a una determinada altura, 
	//	sigue hasta su padre desde esa altura
	// Guarda una pequeña caché de rutas para poder ubicarse fácilmente
	private Map<String, NodoCache> cacheRutas = new HashMap<String, NodoCache>();
	
	/** Carga una caché de propiedades a partir de un determinado pom.xml. */
	public void cargarCache(File xml) {
		raiz = new NodoCache(xml)
	}
	
	/**
	 * Resuelve una propiedad contra la caché a partir de un determinado 
	 * fichero.  De esta manera respetamos la relación jerárquica y además
	 * permitimos que se pisen propiedades si es necesario.
	 */
	public String resolver(String propiedad, File contexto) {
		String ret = null;
		if (cacheRutas.containsKey(contexto.getCanonicalPath())) {
			NodoCache nodo = cacheRutas.get(contexto.getCanonicalPath())
			return resolverI(propiedad, nodo)
		}
		return ret;
	}
	
	// Resuelve la propiedad jerárquicamente contra las propiedades definidas
	//	en el nodo; si no es posible, contra las de su padre; etc.
	private String resolverI(String propiedad, NodoCache nodo) {
		String ret = null;
		if (nodo != null) {
			if (nodo.propiedad(propiedad) != null) {
				ret = nodo.propiedad(propiedad);
			}
			else {
				ret = resolverI(propiedad, nodo.padre())
			}
		}
		return ret;
	}
	
	/**
	 * Un nodo de la caché es un pom.xml con sus propiedades
	 */
	private class NodoCache {
		
		// Ruta del fichero
		private File xml;
		// Hijos del nodo de la caché
		private List<NodoCache> hijos;
		// Padre del nodo de la caché
		private NodoCache padre = null;
		// Propiedades definidas en este nodo
		private Map<String, String> propiedades;
		
		// Constructor público
		public NodoCache(File xml) {
			this(null, xml);
		}
		
		// Padre del nodo
		public NodoCache padre() {
			return padre;
		}
		
		// Valor de la propiedad solicitada
		public String propiedad(String propiedad) {
			String ret = null
			if (propiedades != null) {
				ret = propiedades.get(propiedad);
			}
			return ret
		}
		
		// Construye un nodo a partir de un fichero XML
		private NodoCache(NodoCache padre, File xml) {
			if (xml == null) {
				throw new NullPointerException("La ruta no puede ser null");
			}
			CachePropiedades.this.cacheRutas.put(xml.getCanonicalPath(), this);
			this.padre = padre;
			this.xml = xml;
			
			if (xml.exists()) {
				hijos = new LinkedList<NodoCache>();
				propiedades = new HashMap<String, String>();
				def xmlObj = new XmlSlurper().parse(xml);
				cargarPropiedades(xmlObj);
				anyadirHijos(xmlObj);
			}
			
		}
		
		// Carga las propiedades de este nodo
		private void cargarPropiedades(xmlObj) {
			if (xmlObj.properties != null && xmlObj.properties.size() > 0) {
				xmlObj.properties.children().each {
					propiedades.put(it.name(), it.text())
				}
			}
		}
		
		// Añade las hijos del nodo
		private void anyadirHijos(xmlObj) {
			if (xmlObj.modules != null && xmlObj.modules.size() > 0) {
				xmlObj.modules.module.each { module ->
					def directorioXML = this.xml.getCanonicalPath().substring(0, this.xml.getCanonicalPath().lastIndexOf(File.separator))
					def rutaHijo = directorioXML + File.separator + module.text() + File.separator + "pom.xml";
					hijos << new NodoCache(this, new File(rutaHijo));
				}
			}
		}
		
		@Override
		public String toString() {
			return xml.getCanonicalPath()
		}
	}
	
}
