package es.eci.utils

/**
 * Esta clase modela una validación de parámetros.
 */
public class ParameterValidator {

	// Validaciones a ejecutar
	private List<Validation> validations;
	
	/**
	 * Este método valida los parámetros indicados en el builder.
	 * @throws Exception Si algún parámetro era null, 
	 */
	public void validate() throws Exception {
		List<String> errors = new LinkedList<String>();
		for (Validation validation: validations) {
			Closure validationMethod = validation.getClosure();
			if (validationMethod(validation.getActual()) == false) {
				errors.add(String.format("Parameter %s [%s] is invalid", 
					validation.getParam(), validation.getActual()));
			}			
		}
		if (errors.size() > 0) {
			StringBuilder sb = new StringBuilder();
			sb.append("\n====================================");
			sb.append("\nVALIDATION ERRORS");
			sb.append("\n====================================");
			for (String error: errors) {
				sb.append("\n");
				sb.append(error);
			}
			throw new Exception(sb.toString());
		}
	}
	
	// Construye el validador
	ParameterValidator(List<Validation> validations) {
		this.validations = validations;
	}
	
	public static class Builder {
		
		private List<Validation> validations;
		
		// Construye un Builder
		Builder() {
			validations = new LinkedList<String>();
		}
		
		// Añade una validación al constructor
		public Builder add(String param, Object actual) {
			validations.add(new Validation(param, actual));
			return this;
		}
		
		// Añade una validación al constructor
		public Builder add(String param, Object actual, Closure closure) {
			validations.add(new Validation(param, actual, closure));
			return this;
		}
		
		// Concluye la construcción
		public ParameterValidator build() {
			return new ParameterValidator(validations);
		}
	}

	// Referencia al Builder
	public static Builder builder() {
		return new Builder();
	}
	
	
	// Validación atómica con el nombre del parámetro y valor real
	private static class Validation {
		private String param;
		private Object actual;
		// Acepta un único parámetro: el actual
		// Devuelve true si está bien, false si está mal
		private Closure closure;
		
		// Construye una validación informada
		public Validation(String param, Object actual) {
			// Por defecto se limita a comprobar que el objeto no sea nulo
			this(param, actual, { 
					if (actual instanceof String) {
						return StringUtil.notNull(actual)
					}
					else {
						return actual != null
					} 
				}
			)
		}
		
		// Construye una validación informada
		public Validation(String param, Object actual, Closure closure) {
			this.param = param;
			this.actual = actual;
			this.closure = closure;
		}

		/**
		 * @return the param
		 */
		public String getParam() {
			return param;
		}

		/**
		 * @return the actual
		 */
		public Object getActual() {
			return actual;
		}

		/**
		 * @return the closure
		 */
		public Closure getClosure() {
			return closure;
		}
		
		
	}	
}
