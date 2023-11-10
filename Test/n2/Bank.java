package Ex1;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

class Bank {

    private ReentrantLock lockerBank = new ReentrantLock();

    private static class Account {

        private ReentrantLock lockerAccount = new ReentrantLock();
        private int balance;
        Account(int balance) { this.balance = balance; }
        int balance() { return balance; }
        boolean deposit(int value) {
            balance += value;
            return true;
        }
        boolean withdraw(int value) {
            if (value > balance)
                return false;
            balance -= value;
            return true;
        }
    }

    private Map<Integer, Account> map = new HashMap<Integer, Account>();
    private int nextId = 0;

    // create account and return account id
    public int createAccount(int balance) {
        Account c = new Account(balance);
        lockerBank.lock();
        try{
            int id = nextId;
            nextId += 1;
            map.put(id, c);
            return id;
        }
        finally{
            lockerBank.unlock();
        }
    }
    
    // close account and return balance, or 0 if no such account
    public int closeAccount(int id) {
        lockerBank.lock();
        Account c = map.remove(id);
        try{
            if (c == null)
            return 0;
            return c.balance();
            c.lockerAccount.lock();
            lockerBank.unlock();
        }
        finally{
            c.lockerAccount.unlock();
        }
        
    }

    // account balance; 0 if no such account
    public int balance(int id) {
        lockerBank.lock();
        Account c = map.get(id);
        try{
            if (c == null)
                return 0;
                c.lockerAccount.lock();
                lockerBank.unlock();
                return c.balance();
        }
        finally{
                c.lockerAccount.unlock();
        }
    }

    // deposit; fails if no such account
    public boolean deposit(int id, int value) {
        lockerBank.lock();
        Account c = map.get(id);
        try{
            if (c == null)
            return false;
            c.lockerAccount.lock();
            return c.deposit(value);
            c.lockerAccount.unlock();
        }
        finally{
            lockerBank.unlock();
        }
    }

    // withdraw; fails if no such account or insufficient balance
    public boolean withdraw(int id, int value) {
        lockerBank.lock();
        Account c = map.get(id);
        try{
            if (c == null)
                return false;
                return c.withdraw(value);
        }
        finally{
            lockerBank.unlock();
        }
    }

    // transfer value between accounts;
    // fails if either account does not exist or insufficient balance
    public boolean transfer(int from, int to, int value) {
        Account cfrom, cto;
        lockerBank.lock();
        cfrom = map.get(from);
        cto = map.get(to);
        try{
            if (cfrom == null || cto ==  null)
                return false;
            cto.lockerAccount.lock();
            cfrom.lockerAccount.lock();
            lockerBank.unlock();
            return cfrom.withdraw(value) && cto.deposit(value);
        }
        finally{
            cto.lockerAccount.unlock();
            cfrom.lockerAccount.unlock();
        }
    }

    // sum of balances in set of accounts; 0 if some does not exist
    public int totalBalance(int[] ids) {
        int total = 0;
        for (int i : ids) {
            lockerBank.lock();
            Account c = map.get(i);
            if (c == null)
                return 0;
            c.lockerAccount.lock();
            total += c.balance();
            c.lockerAccount.unlock();
        }

        for (int i : ids){
            lockerBank.unlock();
            
        }
        return total;
    }

}