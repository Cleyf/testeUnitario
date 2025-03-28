package br.ce.wcaquino.servicos;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Locacao;

import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;
import org.junit.*;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static br.ce.wcaquino.builders.FilmeBuilder.umFilme;
import static br.ce.wcaquino.builders.FilmeBuilder.umFilmeSemEstoque;
import static br.ce.wcaquino.builders.LocacaoBuilder.umLocacao;
import static br.ce.wcaquino.builders.UsuarioBuilder.umUsuario;
import static br.ce.wcaquino.matchers.MatchersProprios.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LocacaoService.class)
public class LocacaoServiceTest {
    @InjectMocks
    private LocacaoService service;
    @Mock
    private SPCService spc;
    @Mock
    private LocacaoDAO dao;
    @Mock
    private EmailService email;

    @Rule
    public ErrorCollector error = new ErrorCollector();

    @Rule
    public ExpectedException exception = ExpectedException.none();



    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void deveAlugarFilme() throws Exception {
        Assume.assumeFalse(DataUtils.verificarDiaSemana(new Date(), Calendar.SATURDAY));

        //cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = Arrays.asList(umFilme().comValor(5.0).agora());

        //acao
        Locacao locacao = service.alugarFilme(usuario, filmes);
            //verificacao
        error.checkThat(locacao.getValor(), is(equalTo(5.0)));

        error.checkThat(locacao.getDataLocacao(), ehHoje());
        //error.checkThat(isMesmaData(locacao.getDataRetorno(), obterDataComDiferencaDias(1)), is(true));
        //error.checkThat(locacao.getDataLocacao(), ehHojeComDiferencaDias(1));
    }

    @Test(expected = FilmeSemEstoqueException.class)
    public void deveLancarExcecaoAoAlugarFilmeSemEstoque() throws Exception {
        //cenario

        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = Arrays.asList(umFilmeSemEstoque().agora());

        //acao
        service.alugarFilme(usuario, filmes);
    }

    @Test
    public void naoDeveAlugarFilmeSemUsuario() throws FilmeSemEstoqueException {
        //cenario
        LocacaoService service =new LocacaoService();
        List<Filme> filmes = Arrays.asList(umFilme().agora());

        //acao
        try {
            service.alugarFilme(null, filmes);
            Assert.fail();
        } catch (LocadoraException e) {
            assertThat(e.getMessage(), is("Usuario vazio"));
        }
    }

    @Test
    public void naoDeveAlugarFilmeSemFIlme() throws FilmeSemEstoqueException, LocadoraException {
        //cenario
        Usuario usuario = umUsuario().agora();

        exception.expect(LocadoraException.class);
        exception.expectMessage("Filme vazio");

        //acao
        service.alugarFilme(usuario, null);
    }

    @Test
    public void deveDevolverNaSegundaAoAlugarNoSabado() throws Exception {


        //cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = Arrays.asList(umFilme().agora());

        PowerMockito.whenNew(Date.class).withNoArguments().thenReturn(DataUtils.obterData(29,4,2017));
        //acao
        Locacao retorno = service.alugarFilme(usuario, filmes);
        /*Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 29);
        calendar.set(Calendar.MONTH, Calendar.APRIL);
        calendar.set(Calendar.YEAR, 2017);
        PowerMockito.mockStatic(Calendar.class);
        PowerMockito.when(Calendar.getInstance()).thenReturn(calendar);*/
        //verificacao
        assertThat(retorno.getDataRetorno(), caiNumaSegunda());

        /*boolean ehSegunda = DataUtils.verificarDiaSemana(retorno.getDataRetorno(), Calendar.MONDAY);
        Assert.assertTrue(ehSegunda);
       // assertThat(retorno.getDataRetorno(), new DiaSemanaMatcher(Calendar.MONDAY));
        assertThat(retorno.getDataRetorno(), caiEm(Calendar.SUNDAY));
        assertThat(retorno.getDataRetorno(), caiNumaSegunda());*/
    }

    @Test
    public void naoDeveAlugarFilmeParaNegativadoSPC() throws Exception {
        //cenario
        Usuario usuario = umUsuario().agora();

        List<Filme> filmes = Arrays.asList(umFilme().agora());

        when(spc.possuiNegativacao(Mockito.any(Usuario.class))).thenReturn(true);

        //acao
        try {
            service.alugarFilme(usuario, filmes);
            Assert.fail();
        } catch (LocadoraException e) {
            Assert.assertThat(e.getMessage(), is("Usuario Negativado"));
        }
        //verificacao
        verify(spc).possuiNegativacao(usuario);
    }

    @Test
    public void deveEnviarEmailParaLocacoesAtrasadas() {
        //cenario
        Usuario usuario = umUsuario().agora();
        Usuario usuario2 = umUsuario().comNome("Usuario em dia").agora();
        Usuario usuario3 = umUsuario().comNome("Outro atrasado").agora();
        List<Locacao> locacoes =
                Arrays.asList(
                        umLocacao().atrasado().comUsuario(usuario).agora(),
                        umLocacao().comUsuario(usuario2).agora(),
                        umLocacao().atrasado().comUsuario(usuario3).agora(),
                        umLocacao().atrasado().comUsuario(usuario3).agora());
        when(dao.obterLocacoesPendentes()).thenReturn(locacoes);

        //acao
        service.notificarAtrasos();

        //verificacao
        verify(email, times(3)).notificarAtraso(Mockito.any(Usuario.class));
        verify(email).notificarAtraso(usuario);
        verify(email, Mockito.atLeastOnce()).notificarAtraso(usuario3);
        verify(email, never()).notificarAtraso(usuario2);
        verifyNoMoreInteractions(email);
    }

    @Test
    public void deveTratarErronoSPC() throws Exception {
        //cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = Arrays.asList(umFilme().agora());

        when(spc.possuiNegativacao(usuario)).thenThrow(new Exception("Falha catastrófica"));

        exception.expect(LocadoraException.class);
        exception.expectMessage("Problemas com SPC, tente novamente");

        //acao
        service.alugarFilme(usuario, filmes);

        //verificacao
    }

    @Test
    public void deveProrrogarUmaLocacao() {
        //cenario
        Locacao locacao = umLocacao().agora();

        //acao
        service.prorrogarLocacao(locacao, 3);

        //verificacao
        ArgumentCaptor<Locacao> argCapt = ArgumentCaptor.forClass(Locacao.class);
        Mockito.verify(dao).salvar(argCapt.capture());
        Locacao locacaoRetornada = argCapt.getValue();

        error.checkThat(locacaoRetornada.getValor(), is(12.0));
        error.checkThat(locacaoRetornada.getDataLocacao(), ehHoje());
        error.checkThat(locacaoRetornada.getDataRetorno(), ehHojeComDiferencaDias(3));
    }

}
