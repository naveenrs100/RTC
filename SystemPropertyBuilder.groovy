package es.eci.utils

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.lang.reflect.Method

import es.eci.utils.base.Loggable;

/**
 * Puebla un objeto con las propiedades param.XXX que encuentre definidas en 
 * el sistema, o bien devuelve un map con las mismas.
 * 
 * La clase tolera argumentos de tipo
 * 
 * String
 * Boolean/boolean
 * Integer/int
 * Long/long
 * Double/double
 * Float/float
 * File
 * Date (pero debe especificarse un conversor, de lo contrario usa dd/MM/yyyy)
 * 
 */
class SystemPropertyBuilder extends Loggable {
	
	//------------------------------------------------------------------------
	// Propiedades de la clase
	
	// Conversor de fechas
	private DateFormat df = new SimpleDateFormat("dd/MM/yyyy")
	
	//------------------------------------------------------------------------
	// Métodos de la clase	
	
	/**
	 * Recupera los parámetros indicados en la entrada como map, descartando
	 * la raíz 'param.'
	 * @return Map con los parámetros y su valor (descartando la raíz 'param.' 
	 * en el nombre de parámetro).
	 */
	public Map<String, String> getSystemParameters() {
		Map<String, String> ret = new HashMap<String, String>();
		System.properties.each { propertyEntry ->
			def property = propertyEntry.key
			def value = propertyEntry.value			
			if (property.startsWith('param.')) {
				log("Se incluye la property ${propertyEntry}");				
				ret.put(property.replaceAll("param\\.", "").trim(), 
					System.getProperty(property).trim());
			}
		}
		return ret;
	}
	
	/**
	 * Nos interesan los métodos con un solo parámetro de tipo:
	 * String
	 * Boolean/boolean
	 * Integer/int
	 * Long/long
	 * Double/double
	 * Float/float
	 * Date 
	 * @param types Array de tipos.
	 * @return Cierto si hay un solo parámetro y está dentro de los tipos
	 * contemplados en esta clase.
	 */
	private boolean validateParameters(Class[] types) {
		boolean ret = false;
		if (types != null && types.length == 1) {
			ret = [String.class, 
				   Boolean.class, 
				   boolean.class,
				   Integer.class, 
				   int.class,
				   Long.class,
				   long.class, 
				   Double.class,
				   double.class, 
				   Float.class, 
				   float.class,
				   File.class,
				   Date.class].contains(types[0])
		}
		return ret;
	}
	
	/**
	 * Este método obtiene un mapa que contiene los setters de propiedad
	 * de la clase, indexados por la propiedad referida.
	 * @param clazz Clase a obtener los métodos setter.
	 * @return Tabla de métodos setter de propiedad, indexados por nombre
	 * de la misma.
	 */
	private Map<String, Method> getSettersMap(Class clazz) {
		Map<String, Method> ret = new HashMap<String, Method>();
		Method[] theMethods = clazz.getMethods();
		theMethods.each { Method method ->
			// Calcular el nombre de la propiedad
			String methodName = method.getName();
			if (methodName.startsWith("set") 
					&& methodName.length() > 3
					&& validateParameters(method.getParameterTypes())) {
				String property = methodName.replaceFirst("set", "");
				// Primera letra a minúscula
				property = property.charAt(0).toLowerCase().toString() + property.substring(1);
				ret[property] = method;
			}
		}
		return ret;
	}
	
	/**
	 * Intenta poblar el objeto con los parámetros recibidos desde la invocación.
	 * Se considera parámetros todas aquellas propiedades de sistema que empiecen
	 * por param. 
	 * @param object Objeto a poblar.
	 */
	public void populate(Object object) {
		long millis = Stopwatch.watch {
			// Obtener de System las propiedades que empiecen por param
			Map<String, String> params = getSystemParameters();
			Class clazz = object.class;
			Map<String, Method> setters = getSettersMap(clazz);
			for (String property: params.keySet()) {
				Method method =  setters[property];
				if (method != null) {
					String value = params[property];
					if (!value.startsWith("\$")) {
						Class[] types = method.getParameterTypes();
						// Se adopta el del primer parámetro
						if (types[0].equals(String.class)) {
							method.invoke(object, value);
						}
						else if (types[0].equals(Integer.class) || types[0].equals(int.class)) {
							method.invoke(object, Integer.valueOf(value));
						}
						else if (types[0].equals(Long.class) || types[0].equals(long.class)) {
							method.invoke(object, Long.valueOf(value));
						}
						else if (types[0].equals(Double.class) || types[0].equals(double.class)) {
							method.invoke(object, Double.valueOf(value));
						}
						else if (types[0].equals(Float.class) || types[0].equals(float.class)) {
							method.invoke(object, Float.valueOf(value));
						}
						else if (types[0].equals(Boolean.class) || types[0].equals(boolean.class)) {
							method.invoke(object, Utiles.toBoolean(value));
						}
						else if (types[0].equals(File.class)) {
							method.invoke(object, new File(value));
						}
						else if (types[0].equals(Date.class)) {
							method.invoke(object, df.parse(value));
						}
					}
				}
			}
		}
		log "Objeto $object poblado en $millis milisegundos"
	}

	/**
	 * Indica que se debe usar una estrategia distinta para convertir fechas.
	 * @param df Formateador de fechas a utilizar.
	 */
	public void setDateConverter(DateFormat df) {
		this.df = df;
	}
}
