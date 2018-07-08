package es.eci.utils

class ComponentTask implements Runnable {

	//----------------------------------------------------------
	// Propiedades de la clase
	
	// Componente a construir
	private String component;
	// Closure a ejecutar por cada componente
	private Closure closure;
	
	//----------------------------------------------------------
	// Métodos de la clase
	
	/**
	 * Crea una tarea sobre un componente.
	 * @param component Componente de SCM
	 * @param closure Tarea a ejecutar
	 */
	public ComponentTask(String component, Closure closure) {
		this.component = component;
		this.closure = closure;
	}
	
	@Override
	public void run() {
		closure(component);
	}

}
