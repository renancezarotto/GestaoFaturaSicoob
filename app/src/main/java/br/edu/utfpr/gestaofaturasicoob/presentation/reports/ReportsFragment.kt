package br.edu.utfpr.gestaofaturasicoob.presentation.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentReportsBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class ReportsFragment : Fragment() {
    
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    
    private val invoiceController = InvoiceController()
    private val authController = AuthController()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupViewPager()
        setupListeners()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(br.edu.utfpr.gestaofaturasicoob.R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(br.edu.utfpr.gestaofaturasicoob.R.id.textToolbarTitle)?.text = "Relatórios"
            it.findViewById<com.google.android.material.button.MaterialButton>(br.edu.utfpr.gestaofaturasicoob.R.id.buttonBack)?.visibility = View.GONE
        }
    }
    
    private fun setupViewPager() {
        val adapter = ReportsPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "MENSAL"
                1 -> "ANUAL"
                else -> ""
            }
        }.attach()
    }
    
    private fun setupListeners() {
        // FAB removed - export functionality can be added via menu if needed
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("OK") { }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Adapter for ViewPager2 to display Monthly and Annual reports
     */
    private inner class ReportsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> MonthlyReportFragment()
                1 -> AnnualReportFragment()
                else -> MonthlyReportFragment()
            }
        }
    }
}
