package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import br.edu.utfpr.gestaofaturasicoob.databinding.BottomSheetEditCategoryBinding
import br.edu.utfpr.gestaofaturasicoob.models.Expense
import java.text.NumberFormat
import java.util.*

/**
 * BottomSheet para editar categoria de despesa
 */
class EditCategoryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditCategoryBinding? = null
    private val binding get() = _binding!!

    private var expense: Expense? = null
    private var onCategorySelected: ((String) -> Unit)? = null

    // Categorias padrão
    private val defaultCategories = listOf(
        "Alimentação",
        "Transporte", 
        "Saúde",
        "Lazer",
        "Educação",
        "Moradia",
        "Vestuário",
        "Combustível",
        "Mercado",
        "Restaurantes",
        "Taxas Cartão",
        "Outros"
    )

    companion object {
        private const val ARG_EXPENSE = "expense"

        fun newInstance(expense: Expense): EditCategoryBottomSheet {
            val fragment = EditCategoryBottomSheet()
            val args = Bundle()
            args.putParcelable(ARG_EXPENSE, expense)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            expense = it.getParcelable(ARG_EXPENSE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        expense?.let { expense ->
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            binding.textSelectedExpense.text = "${expense.date} - ${expense.establishment} - ${currencyFormat.format(expense.value)}"

            // Configurar spinner de categorias
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                defaultCategories
            )
            binding.spinnerCategories.setAdapter(adapter)
            binding.spinnerCategories.setText(expense.category, false)
        }
    }

    private fun setupListeners() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        binding.buttonSave.setOnClickListener {
            val selectedCategory = binding.spinnerCategories.text.toString().trim()
            if (selectedCategory.isNotEmpty()) {
                onCategorySelected?.invoke(selectedCategory)
                dismiss()
            }
        }
    }

    fun setOnCategorySelectedListener(listener: (String) -> Unit) {
        onCategorySelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
