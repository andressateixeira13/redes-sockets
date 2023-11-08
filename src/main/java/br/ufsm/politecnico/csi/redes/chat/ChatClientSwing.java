package br.ufsm.politecnico.csi.redes.chat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import br.ufsm.politecnico.csi.redes.model.Mensagem;


public class ChatClientSwing extends JFrame {
    private  Usuario meuUsuario;
    private  String endBroadcast = "255.255.255.255";
    private DefaultListModel<Usuario> dfListModel;
    private JList<Usuario> listaChat;
    private JTabbedPane tabbedPane = new JTabbedPane();
    private Set<Usuario> chatsAbertos = new HashSet<>();
    private static final int PORT = 8085;
    private static final int TIMEOUT = 30000; // 30 segundos

    public ChatClientSwing() throws UnknownHostException {
        setLayout(new GridBagLayout());
        new Thread(new EnviaSonda()).start();
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Status");

        ButtonGroup group = new ButtonGroup();
        JRadioButtonMenuItem rbMenuItem = new JRadioButtonMenuItem(StatusUsuario.DISPONIVEL.name());
        rbMenuItem.setSelected(true);
        rbMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                ChatClientSwing.this.meuUsuario.setStatus(StatusUsuario.DISPONIVEL);
            }
        });
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(StatusUsuario.NAO_PERTURBE.name());
        rbMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                ChatClientSwing.this.meuUsuario.setStatus(StatusUsuario.NAO_PERTURBE);
            }
        });
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(StatusUsuario.VOLTO_LOGO.name());
        rbMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                ChatClientSwing.this.meuUsuario.setStatus(StatusUsuario.VOLTO_LOGO);
            }
        });
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        menuBar.add(menu);
        this.setJMenuBar(menuBar);

        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if (e.getButton() == MouseEvent.BUTTON3) {
                    JPopupMenu popupMenu = new JPopupMenu();
                    final int tab = tabbedPane.getUI().tabForCoordinate(tabbedPane, e.getX(), e.getY());
                    JMenuItem item = new JMenuItem("Fechar");
                    item.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            PainelChatPVT painel = (PainelChatPVT) tabbedPane.getTabComponentAt(tab);
                            tabbedPane.remove(tab);
                            chatsAbertos.remove(painel.getUsuario());
                        }
                    });
                    popupMenu.add(item);
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        add(new JScrollPane(criaLista()), new GridBagConstraints(0, 0, 1, 1, 0.1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        add(tabbedPane, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.EAST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        setSize(800, 600);
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        final int x = (screenSize.width - this.getWidth()) / 2;
        final int y = (screenSize.height - this.getHeight()) / 2;
        this.setLocation(x, y);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        String nomeUsuario = JOptionPane.showInputDialog(this, "Digite seu nome de usuário: ");
        synchronized (this) {
            this.meuUsuario = new Usuario(nomeUsuario, StatusUsuario.DISPONIVEL, InetAddress.getLocalHost());
            this.notify();
        }
        setVisible(true);
        new Thread(new EnviaSonda()).start();
        new Thread(new RecebeSonda()).start();
        new Thread(new RemoveOfflineUsers()).start();
    }

    private JComponent criaLista() {
        dfListModel = new DefaultListModel<>();
        listaChat = new JList<>(dfListModel);

        listaChat.addMouseListener(new MouseAdapter() {
            @SneakyThrows
            public void mouseClicked(MouseEvent evt) {
                JList<Usuario> list = (JList<Usuario>) evt.getSource();
                if (evt.getClickCount() == 2) {
                    int index = list.locationToIndex(evt.getPoint());
                    if (index >= 0) { // Verifique se o índice é válido
                        Usuario user = list.getModel().getElementAt(index);

                        if (chatsAbertos.contains(user)) {
                            // O usuário já tem uma janela de bate-papo aberta, você pode tomar ação apropriada aqui.
                            // Por exemplo, trazer a janela à frente ou mostrar uma mensagem.
                        } else {
                            try {
                                Socket soc = new Socket(user.getEndereco(), 8086);
                                if (chatsAbertos.add(user)) {
                                    tabbedPane.add(user.toString(), new PainelChatPVT(user, soc));
                                }
                            } catch (ConnectException e) {
                                JOptionPane.showMessageDialog(null, "Erro: Não foi possível conectar ao usuário.", "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
        });

        return listaChat;
    }




    public class RecebeSonda implements Runnable {
        @SneakyThrows
        @Override
        public void run() {
            System.out.println("Entrou em Recebe Sonda");
            DatagramSocket socket = new DatagramSocket(PORT);
            ObjectMapper om = new ObjectMapper();

            while (true) {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                Mensagem sonda = om.readValue(buf, 0, packet.getLength(), Mensagem.class);

                if (sonda.getRemetente() != null) {
                    System.out.println("[SONDA RECEBIDA] ");
                    responderSonda(packet.getAddress());
                    System.out.println(" Resposta: " + packet.getAddress());
                    Usuario novoUsuario = new Usuario(sonda.getRemetente(),
                            StatusUsuario.valueOf(sonda.getStatus()), packet.getAddress());
                    // Verifique se o novo usuário já está na lista
                    boolean usuarioExistente = false;
                    for (int i = 0; i < dfListModel.getSize(); i++) {
                        Usuario usuarioNaLista = dfListModel.getElementAt(i);
                        if (usuarioNaLista.getEndereco().equals(novoUsuario.getEndereco())) {
                            // Usuário já está na lista, atualizo apenas o status
                            usuarioNaLista.setStatus(StatusUsuario.valueOf(sonda.getStatus()));
                            dfListModel.setElementAt(usuarioNaLista, i);
                            usuarioExistente = true;
                            break;
                        }
                    }

                    if (!usuarioExistente) {
                        // Se o usuário não estiver na lista adiciono
                        dfListModel.addElement(novoUsuario);
                    }
                }
            }
        }
    }

    private void responderSonda(InetAddress senderAddress) {
        Mensagem resposta = new Mensagem("sonda", meuUsuario.getNome(), meuUsuario.getStatus().toString(), "");
        EnviaSonda.enviarMensagem(resposta, senderAddress);
    }

    public class EnviaSonda implements Runnable {
        @SneakyThrows
        @Override
        public void run() {
            synchronized (this) {
                if (meuUsuario == null) {
                    this.wait();
                }
            }
            System.out.println("[Envia sonda] ");
            DatagramSocket socket = new DatagramSocket();
            while (true) {
                Mensagem resposta = new Mensagem("sonda", meuUsuario.getNome(), meuUsuario.getStatus().toString(), "");
                System.out.println("Resposta " + resposta);
                enviarMensagem(resposta, InetAddress.getByName("255.255.255.255"));
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public static void enviarMensagem(Mensagem mensagem, InetAddress endereco) {
            try {
                DatagramSocket socket = new DatagramSocket();
                ObjectMapper om = new ObjectMapper();
                byte[] msgJson = om.writeValueAsBytes(mensagem);
                DatagramPacket packet = new DatagramPacket(msgJson, msgJson.length, endereco, PORT);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class PainelChatPVT extends JPanel {
        JTextArea areaChat;
        JTextField campoEntrada;
        Usuario usuario;
        Socket socket;

        PainelChatPVT(Usuario usuario, Socket socket) {
            setLayout(new GridBagLayout());
            areaChat = new JTextArea();
            this.usuario = usuario;
            areaChat.setEditable(false);
            campoEntrada = new JTextField();
            this.socket = socket;

            campoEntrada.addActionListener(new ActionListener() {
                @SneakyThrows
                @Override
                public void actionPerformed(ActionEvent e) {
                    ((JTextField) e.getSource()).setText("");
                    areaChat.append(meuUsuario.getNome() + "> " + e.getActionCommand() + "\n");
                    socket.getOutputStream().write(e.getActionCommand().getBytes());
                }
            });

            campoEntrada.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String mensagem = campoEntrada.getText();
                    if (!mensagem.isEmpty()) {
                        enviarMensagemParaUsuario(mensagem);
                        campoEntrada.setText("");
                    }
                }
            });

            MensagemReceiver receiver = new MensagemReceiver(socket, areaChat);
            receiver.start();
        }

        private void enviarMensagemParaUsuario(String mensagem) {
            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(mensagem.getBytes());
                outputStream.write("\n".getBytes());
                outputStream.flush();
                areaChat.append(meuUsuario.getNome() + "> " + mensagem + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Usuario getUsuario() {
            return usuario;
        }

        public void setUsuario(Usuario usuario) {
            this.usuario = usuario;
        }
    }

    public static void main(String[] args) throws UnknownHostException {
        new ChatClientSwing();
    }

    public enum StatusUsuario {
        DISPONIVEL, NAO_PERTURBE, VOLTO_LOGO
    }

    public class Usuario implements Serializable {
        private String nome;
        private StatusUsuario status;
        private InetAddress endereco;
        private long ultimaResposta;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public StatusUsuario getStatus() {
            return status;
        }

        public void setStatus(StatusUsuario status) {
            this.status = status;
        }

        public InetAddress getEndereco() {
            return endereco;
        }

        public void setEndereco(InetAddress endereco) {
            this.endereco = endereco;
        }

        public Usuario(String nome, StatusUsuario status, InetAddress endereco) {
            this.nome = nome;
            this.status = status;
            this.endereco = endereco;
        }

        public long getUltimaResposta() {
            return ultimaResposta;
        }

        public void setUltimaResposta(long ultimaResposta) {
            this.ultimaResposta = ultimaResposta;
        }

        @Override
        public String toString() {
            return nome + " (" + status.toString() + ")";
        }
    }

    public class MensagemReceiver extends Thread {
        private Socket socket;
        private JTextArea textArea;

        public MensagemReceiver(Socket socket, JTextArea textArea) {
            this.socket = socket;
            this.textArea = textArea;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String message;
                while ((message = reader.readLine()) != null) {
                    textArea.append(message + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class RemoveOfflineUsers extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(30000); // Verificar a cada 30 segundos
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                SwingUtilities.invokeLater(() -> {
                    synchronized (dfListModel) {
                        List<Usuario> usuariosParaRemover = new ArrayList<>();
                        for (int i = 0; i < dfListModel.getSize(); i++) {
                            Usuario user = dfListModel.getElementAt(i);
                            if (user.getStatus() != StatusUsuario.DISPONIVEL) { // Verifica se o status não é DISPONÍVEL
                                usuariosParaRemover.add(user);
                                chatsAbertos.remove(user);
                                fecharAbaDoUsuario(user.getEndereco());
                            }
                        }

                        for (Usuario user : usuariosParaRemover) {
                            System.out.println("Remove: " + user);
                            dfListModel.removeElement(user);
                        }
                    }
                });

            }
        }

        private void fecharAbaDoUsuario(InetAddress userAddress) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                PainelChatPVT painel = (PainelChatPVT) tabbedPane.getComponentAt(i);
                if (painel.getUsuario().getEndereco().equals(userAddress)) {
                    tabbedPane.remove(i);
                    chatsAbertos.remove(painel.getUsuario());
                    break;
                }
            }
        }
    }

}
