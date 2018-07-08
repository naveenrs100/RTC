package es.eci.utils

import com.cloudbees.plugins.flow.FlowCause

import es.eci.utils.base.Loggable;
import hudson.model.AbstractBuild
import hudson.model.Cause;
import hudson.model.Hudson;

/**
 * Esta clase implementa una gestión de variables globales en un build de
 * jenkins.  Un build muy ramificado, consecuencia de la concatenación de
 * varios build flows y trigger/call another build, puede acceder desde
 * las hojas al primer build de todos (la raíz del árbol).  En este job
 * se puede almacenar, como parámetros del mismo,  valores que se comportan
 * como variables globales en las hojas, no importa a qué altura.
 * 
 * El propósito último de esta clase es evitar que se necesiten ficheros temporales
 * durante una construcción.
 */
class GlobalVars extends Loggable {

	//-----------------------------------------------
	// Métodos de la clase
	
	/**
	 * Devuelve la causa de una determinada ejecución.
	 * @param run Ejecución cuya causa necesitamos obtener.
	 * @return La causa de la ejecución si la tuviera, null en otro caso.
	 */
	private Cause getCause(AbstractBuild run) {
	  def cause = null;
	  cause = run.getCause(Cause.UpstreamCause);
	  if (cause == null) {
	    cause = run.getCause(FlowCause);
	  }
	  return cause;
	}
	
	/**
	 * Dada una causa, devuelve la ejecución correspondiente.
	 * @param cause Causa de una ejecución.
	 * @return Ejecución que ha disparado dicha causa.
	 */
	private AbstractBuild getParentRun(Cause cause) {
	  def run = null;
	  if (cause instanceof Cause.UpstreamCause) {
	    def name = ((Cause.UpstreamCause)cause).getUpstreamProject()
		def buildNumber = ((Cause.UpstreamCause)cause).getUpstreamBuild()
	    run = Hudson.instance.getJob(name).getBuildByNumber(Integer.valueOf(buildNumber))
	  }
	  else if (cause instanceof FlowCause) {
	    run = ((FlowCause) cause).getFlowRun()
	  }
	    
	  return run;
	}
	
	/**
	 * Obtiene, a partir de una ejecución de un job en jenkins, la raíz del
	 * árbol de llamadas de esa ejecución.  Por ejemplo, para un árbol:
	 * 
	 * GIS - Mi Corriente - DESARROLLO - build
	 * Trigger
	 * GIS - Mi Corriente - DESARROLLO -COMP- Mi Componente
	 * WorkflowMavenBuild
	 * Controller
	 * stepCompileMaven
	 * 
	 * Si este método se llama desde cualquier altura del árbol, devuelve la 
	 * ejecución de 'GIS - Mi Corriente - DESARROLLO - build' que haya desencadenado
	 * todas las demás. 
	 * @param run Ejecución actual.
	 * @return Ejecución padre de la ejecución actual.
	 */
	private AbstractBuild getRootBuild(AbstractBuild run) {
	  def list = []
	  getRootBuildI(run, list)
	  if (list == null || list.size() == 0) {
		  // Caso trivial: accedemos desde la raíz del árbol de ejecución
		  list.add(run)
	  }
	  return list!=null && list.size() > 0?list[list.size()-1]:null
	}
	
	/**
	 * Inmersión recursiva de la búsqueda del build padre.  Se limita a 
	 *	ir obteniendo el build asociado a cada causa, acumulándolos en la lista.
	 *	El padre de todos será el último de la lista
	 * @param run Ejecución actual.
	 * @param list Lista sobre la que se acumulan los resultados
	 */
	private void getRootBuildI(AbstractBuild run, List<AbstractBuild> list) {
	  def cause = getCause(run)
	  if (cause != null) {
	    def father = getParentRun(cause)
	    if (father != null) {
	      list.add(father)
	      getRootBuildI(father, list)
	    }
	  }
	}
	
	/**
	 * Obtiene el padre directo de un build.
	 * @param build Build cuyo padre queremos obtener
	 * @return Padre del build, null si no lo tiene o no lo podemos obtener
	 */
	public AbstractBuild getParentBuild(AbstractBuild build) {
		AbstractBuild ret = null;
		Cause cause = getCause(build);
		if (cause != null) {
			ret = getParentRun(cause);
		}
		return ret;
	}
	
	/**
	 * Construye un objeto de acceso a variables globales a partir de 
	 * la ejecución actual.
	 */
	public GlobalVars() {
	}
	
	/**
	 * Obtiene el valor de una variable global.
	 * @param build Job a cuyas globales se quiere acceder
	 * @param variable Nombre de la variable.
	 * @return Valor de la variable, si está definida.  Null en otro caso.
	 */
	public String get(AbstractBuild build, String variable) {
		AbstractBuild root = getRootBuild(build);
		String ret = ParamsHelper.getParam(root, variable);
		log ("Root build:: " + root);
		log("Variable global:: $variable -> $ret")
		return ret;
	}	
	
	/**
	 * Elimina una variable global.
	 * @param build Job a cuyas globales se quiere acceder
	 * @param variable Nombre de la variable a eliminar.
	 */
	public void delete(AbstractBuild build, String variable) {
		AbstractBuild root = getRootBuild(build);
		log ("Root build:: " + root);
		log("Eliminando variable global:: $variable ...")
		ParamsHelper.deleteParams(root, variable);
	}
	
	/** 
	 * Actualiza el valor de una variable global.
	 * @param build Job a cuyas globales se quiere acceder
	 * @param variable Nombre de la variable a actualizar.
	 * @param value Valor a asignar a la variable.
	 */
	public void put(AbstractBuild build, String variable, String value) {
		AbstractBuild root = getRootBuild(build);
		log ("Root build:: " + root);
		delete(build, variable);
		log("Asignando variable global:: $variable <- $value")
		Map<String, String> map = new HashMap<String, String>();
		map.put(variable, value);
		ParamsHelper.addParams(root, map);
	}
}
