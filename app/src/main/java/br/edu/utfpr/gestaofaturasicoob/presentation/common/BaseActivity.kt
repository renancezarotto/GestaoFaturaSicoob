package br.edu.utfpr.gestaofaturasicoob.presentation.common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Base Activity with common functionality for all Activities
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {
    
    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding is not available")
    
    /**
     * Create the binding for this activity
     */
    protected abstract fun createBinding(): VB
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = createBinding()
        setContentView(binding.root)
        setupUI()
        observeData()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
    
    /**
     * Setup UI components
     */
    protected open fun setupUI() {
        // Override in subclasses
    }
    
    /**
     * Observe data and update UI
     */
    protected open fun observeData() {
        // Override in subclasses
    }
    
    /**
     * Show a snackbar with a message
     */
    protected fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, message, duration).show()
    }
    
    /**
     * Show a snackbar with an action
     */
    protected fun showSnackbarWithAction(
        message: String,
        actionText: String,
        action: () -> Unit,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        Snackbar.make(binding.root, message, duration)
            .setAction(actionText) { action() }
            .show()
    }
}
