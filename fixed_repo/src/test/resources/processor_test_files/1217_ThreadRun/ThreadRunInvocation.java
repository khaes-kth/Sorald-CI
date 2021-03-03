class ThreadRunInvocation {
  public static void main(String[] args) {
    Runnable runnable = null;

    Thread myThread = new Thread(runnable);
    myThread.start();

    Thread myThread2 = new Thread(runnable);
    myThread2.start();

    run();
  }
}