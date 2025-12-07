package br.edu.utfpr.gestaofaturasicoob.presentation.categories

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.CategoryController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentManageCategoriesBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ManageCategoriesFragment : Fragment() {
    
    private var _binding: FragmentManageCategoriesBinding? = null
    private val binding get() = _binding!!
    
    private val categoryController = CategoryController()
    private val authController = AuthController()
    
    private lateinit var defaultCategoriesAdapter: CategoriesAdapter
    private lateinit var customCategoriesAdapter: CategoriesAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        loadCategories()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<TextView>(R.id.textToolbarTitle)?.text = "Gerenciar Categorias"
            val backButton = it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)
            backButton?.visibility = View.VISIBLE
            backButton?.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }
    
    private fun setupRecyclerViews() {
        // Categorias Padrão
        defaultCategoriesAdapter = CategoriesAdapter(
            onEditClick = { category -> showEditCategoryDialog(category) },
            onDeleteClick = null // Categorias padrão não podem ser excluídas
        )
        binding.recyclerDefaultCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = defaultCategoriesAdapter
        }
        
        // Categorias Personalizadas
        customCategoriesAdapter = CategoriesAdapter(
            onEditClick = { category -> showEditCategoryDialog(category) },
            onDeleteClick = { category -> confirmDeleteCategory(category) }
        )
        binding.recyclerCustomCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customCategoriesAdapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddCategory.setOnClickListener {
            showCreateCategoryDialog()
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            loadCategories()
        }
    }
    
    private fun loadCategories() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                val result = categoryController.getCategories(userId)
                
                if (result.isSuccess) {
                    val allCategories = result.getOrNull() ?: emptyList()
                    val defaultCategories = allCategories.filter { it.isDefault }
                    val customCategories = allCategories.filter { !it.isDefault }
                    
                    defaultCategoriesAdapter.submitList(defaultCategories)
                    customCategoriesAdapter.submitList(customCategories)
                    
                    // Mostrar/esconder estado vazio
                    binding.emptyStateLayout.visibility = 
                        if (customCategories.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    showError("Erro ao carregar categorias")
                }
            } catch (e: Exception) {
                showError("Erro: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun showCreateCategoryDialog() {
        showCategoryDialog(null)
    }
    
    private fun showEditCategoryDialog(category: Category) {
        if (category.isDefault) {
            showError("Categorias padrão não podem ser editadas")
            return
        }
        showCategoryDialog(category)
    }
    
    private fun showCategoryDialog(existingCategory: Category?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_category, null)
        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val titleText = dialogView.findViewById<TextView>(R.id.textDialogTitle)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.editTextName)
        val recurringSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switchRecurring)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonSave)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonCancel)
        
        // Configurar título
        titleText.text = if (existingCategory == null) "Nova Categoria" else "Editar Categoria"
        
        // Preencher dados se for edição
        if (existingCategory != null) {
            nameInput.setText(existingCategory.name)
            recurringSwitch.isChecked = existingCategory.isRecurring
        }
        
        // Nota: Seleção de cor removida - categorias não usam mais cores individuais
        
        // Botões
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isBlank()) {
                nameInput.error = "Nome é obrigatório"
                return@setOnClickListener
            }
            
            val category = Category(
                id = existingCategory?.id ?: "",
                name = name,
                color = "", // Categorias não usam mais cores individuais
                isRecurring = recurringSwitch.isChecked,
                isDefault = false
            )
            
            dialog.dismiss()
            saveCategory(category)
        }
        
        dialog.show()
    }
    
    private fun saveCategory(category: Category) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                val result = categoryController.createCategory(userId, category)
                
                if (result.isSuccess) {
                    showSuccess(if (category.id.isEmpty()) "Categoria criada!" else "Categoria atualizada!")
                    loadCategories()
                } else {
                    showError("Erro ao salvar categoria")
                }
            } catch (e: Exception) {
                showError("Erro: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun confirmDeleteCategory(category: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Excluir Categoria")
            .setMessage("Tem certeza que deseja excluir a categoria \"${category.name}\"?")
            .setPositiveButton("Excluir") { _, _ ->
                deleteCategory(category)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun deleteCategory(category: Category) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                val result = categoryController.deleteCategory(userId, category.id)
                
                if (result.isSuccess) {
                    showSuccess("Categoria excluída!")
                    loadCategories()
                } else {
                    showError("Erro ao excluir categoria")
                }
            } catch (e: Exception) {
                showError("Erro: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Adapter para lista de categorias
    private class CategoriesAdapter(
        private val onEditClick: (Category) -> Unit,
        private val onDeleteClick: ((Category) -> Unit)?
    ) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {
        
        private var categories = listOf<Category>()
        
        fun submitList(newCategories: List<Category>) {
            categories = newCategories
            notifyDataSetChanged()
        }
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textName: TextView = view.findViewById(R.id.textCategoryName)
            val textRecurring: TextView = view.findViewById(R.id.textRecurring)
            val textDefault: TextView = view.findViewById(R.id.textDefault)
            val buttonEdit: com.google.android.material.button.MaterialButton = view.findViewById(R.id.buttonEdit)
            val buttonDelete: com.google.android.material.button.MaterialButton = view.findViewById(R.id.buttonDelete)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_manage, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            
            holder.textName.text = category.name
            holder.textRecurring.visibility = if (category.isRecurring) View.VISIBLE else View.GONE
            holder.textDefault.visibility = if (category.isDefault) View.VISIBLE else View.GONE
            
            // Mostrar botão de editar apenas para categorias personalizadas
            if (!category.isDefault) {
                holder.buttonEdit.visibility = View.VISIBLE
                holder.buttonEdit.setOnClickListener {
                    onEditClick(category)
                }
            } else {
                holder.buttonEdit.visibility = View.GONE
            }
            
            // Mostrar botão de excluir apenas para categorias personalizadas
            if (onDeleteClick != null && !category.isDefault) {
                holder.buttonDelete.visibility = View.VISIBLE
                holder.buttonDelete.setOnClickListener {
                    onDeleteClick.invoke(category)
                }
            } else {
                holder.buttonDelete.visibility = View.GONE
            }
        }
        
        override fun getItemCount() = categories.size
    }
    
    // ColorAdapter removido - categorias não usam mais cores individuais
}

