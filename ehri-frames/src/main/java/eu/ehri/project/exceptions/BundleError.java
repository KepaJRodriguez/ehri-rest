package eu.ehri.project.exceptions;

import com.google.common.collect.ListMultimap;

/**
 * Exception that has a nested structure to represent
 * errors that occur validating or saving a bundle.
 * 
 * @author michaelb
 *
 */
@SuppressWarnings("serial")
public abstract class BundleError extends Exception {
    
    public static final String ERROR_KEY = "errors";
    public static final String REL_KEY = "relationships";
    
    public BundleError(String message) {
        super(message);
    }
    
    public abstract ListMultimap<String, String> getErrors();
    public abstract ListMultimap<String, BundleError> getRelations();
}